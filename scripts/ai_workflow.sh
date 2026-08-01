#!/usr/bin/env bash

# A helper script for the AI "Accept Editz" workflow

function usage() {
    echo "Usage: $0 [command]"
    echo ""
    echo "Commands:"
    echo "  checkpoint   - Create a checkpoint commit of the current working state"
    echo "  review       - Output a git diff to review AI changes"
    echo "  worktree     - Create a new git worktree for a feature branch"
    exit 1
}

case "$1" in
    checkpoint)
        git add .
        git commit -m "AI checkpoint"
        echo "Checkpoint committed."
        ;;
    review)
        git diff
        ;;
    worktree)
        if [ -z "$2" ]; then
            echo "Error: Must provide a branch name."
            echo "Usage: $0 worktree [branch-name]"
            exit 1
        fi
        git worktree add "../$2"
        echo "Worktree created at ../$2"
        ;;
    *)
        usage
        ;;
esac
