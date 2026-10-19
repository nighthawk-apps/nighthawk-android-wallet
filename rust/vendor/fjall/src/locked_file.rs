// Copyright (c) 2024-present, fjall-rs
// This source code is licensed under both the Apache 2.0 and MIT License
// (found in the LICENSE-* files in the repository)
//
// Nighthawk patch: Rust 1.91 (the F-Droid recipe pin) implements
// `File::try_lock` on Linux but returns `ErrorKind::Unsupported` on Android.
// Android's libc has had `flock` for years, so fall back to that instead of
// failing wallet open with "try_lock() not supported".

#![allow(unsafe_code)]

use std::{
    fs::{File, OpenOptions},
    io::ErrorKind,
    os::unix::io::AsRawFd,
    path::Path,
    sync::Arc,
};

struct LockedFileGuardInner(File);

impl Drop for LockedFileGuardInner {
    fn drop(&mut self) {
        log::debug!("Unlocking database lock");

        if let Err(e) = self.0.unlock() {
            if e.kind() == ErrorKind::Unsupported {
                #[allow(unsafe_code)]
                let rc = unsafe { libc::flock(self.0.as_raw_fd(), libc::LOCK_UN) };
                if rc != 0 {
                    log::warn!(
                        "Failed to unlock database lock: {:?}",
                        std::io::Error::last_os_error()
                    );
                }
            } else {
                log::warn!("Failed to unlock database lock: {e:?}");
            }
        }
    }
}

#[derive(Clone)]
#[expect(unused)]
pub struct LockedFileGuard(Arc<LockedFileGuardInner>);

fn lock_exclusive(file: &File) -> Result<(), crate::Error> {
    match file.try_lock() {
        Ok(()) => Ok(()),
        Err(std::fs::TryLockError::WouldBlock) => Err(crate::Error::Locked),
        Err(std::fs::TryLockError::Error(e)) if e.kind() == ErrorKind::Unsupported => {
            flock_exclusive(file)
        }
        Err(std::fs::TryLockError::Error(e)) => {
            log::error!(
                "Failed to acquire database lock - if this is expected, you can try opening again (maybe wait a little)"
            );
            Err(crate::Error::Io(e))
        }
    }
}

fn flock_exclusive(file: &File) -> Result<(), crate::Error> {
    #[allow(unsafe_code)]
    let rc = unsafe { libc::flock(file.as_raw_fd(), libc::LOCK_EX | libc::LOCK_NB) };
    if rc == 0 {
        return Ok(());
    }
    let err = std::io::Error::last_os_error();
    if err.kind() == ErrorKind::WouldBlock || err.raw_os_error() == Some(libc::EWOULDBLOCK) {
        Err(crate::Error::Locked)
    } else {
        log::error!(
            "Failed to acquire database lock via flock - if this is expected, you can try opening again (maybe wait a little)"
        );
        Err(crate::Error::Io(err))
    }
}

impl LockedFileGuard {
    pub fn create_new(path: &Path) -> crate::Result<Self> {
        log::debug!("Acquiring database lock at {}", path.display());

        let file = match File::create_new(path) {
            Ok(f) => f,
            Err(e) if e.kind() == ErrorKind::AlreadyExists => {
                OpenOptions::new().read(true).write(true).open(path)?
            }
            e => e?,
        };

        lock_exclusive(&file)?;

        Ok(Self(Arc::new(LockedFileGuardInner(file))))
    }

    pub fn try_acquire(path: &Path) -> crate::Result<Self> {
        const RETRIES: usize = 3;

        log::debug!("Acquiring database lock at {}", path.display());

        let file = OpenOptions::new().read(true).write(true).open(path)?;

        for i in 1..=RETRIES {
            match lock_exclusive(&file) {
                Ok(()) => break,
                Err(crate::Error::Locked) => {
                    if i == RETRIES {
                        return Err(crate::Error::Locked);
                    }
                    std::thread::sleep(std::time::Duration::from_millis(100));
                }
                Err(e) => return Err(e),
            }
        }

        Ok(Self(Arc::new(LockedFileGuardInner(file))))
    }
}

#[cfg(test)]
mod tests {
    use super::LockedFileGuard;
    use std::fs::File;

    #[test]
    fn create_new_acquires_lock_when_file_already_exists() -> crate::Result<()> {
        let dir = tempfile::tempdir()?;
        let path = dir.path().join("lock");

        File::create(&path)?;

        let _guard = LockedFileGuard::create_new(&path)?;

        Ok(())
    }

    #[test]
    fn try_acquire_acquires_lock_on_existing_file() -> crate::Result<()> {
        let dir = tempfile::tempdir()?;
        let path = dir.path().join("lock");

        File::create(&path)?;

        let _guard = LockedFileGuard::try_acquire(&path)?;

        Ok(())
    }
}
