//! Per-token balances for portfolio / send picker.

use drk::Drk;

use crate::DrkTokenBalance;

pub async fn list_token_balances(drk: &Drk) -> Result<Vec<DrkTokenBalance>, String> {
    let balances = drk.money_balance().await.map_err(|e| e.to_string())?;
    let aliases = drk
        .get_aliases_mapped_by_token()
        .await
        .map_err(|e| e.to_string())?;

    let mut rows: Vec<DrkTokenBalance> = balances
        .into_iter()
        .map(|(token_id, balance)| {
            let token_id_str = token_id.to_string();
            let display_label = aliases.get(&token_id_str).cloned();
            DrkTokenBalance {
                token_id: token_id_str,
                display_label,
                balance_atomic: i64::try_from(balance).unwrap_or(i64::MAX),
            }
        })
        .collect();

    rows.sort_by(|a, b| {
        b.balance_atomic
            .cmp(&a.balance_atomic)
            .then_with(|| a.token_id.cmp(&b.token_id))
    });

    Ok(rows)
}
