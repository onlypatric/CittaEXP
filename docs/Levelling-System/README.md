# Levelling System - Index

Questa cartella contiene la versione strutturata delle idee per il nuovo sistema levelling città.

## Documenti

1. [00-INDEX.md](/Users/patric/Documents/Minecraft/CittaEXP/docs/Levelling-System/00-INDEX.md)  
   Mappa rapida dei documenti e ordine di lettura consigliato.

2. [01-VISIONE-E-CORE-LOOP.md](/Users/patric/Documents/Minecraft/CittaEXP/docs/Levelling-System/01-VISIONE-E-CORE-LOOP.md)  
   Obiettivo prodotto, ciclo daily/weekly/monthly, contributo città e reward philosophy.

3. [02-CATALOGO-SFIDE.md](/Users/patric/Documents/Minecraft/CittaEXP/docs/Levelling-System/02-CATALOGO-SFIDE.md)  
   Tipologie sfide, esempi target, difficoltà e schema ricompense.

4. [03-ANTI-ABUSE-E-FAIRNESS.md](/Users/patric/Documents/Minecraft/CittaEXP/docs/Levelling-System/03-ANTI-ABUSE-E-FAIRNESS.md)  
   Guardrail anti-farm/alt/AFK e limiti di contribuzione.

5. [04-ROADMAP-IMPLEMENTAZIONE.md](/Users/patric/Documents/Minecraft/CittaEXP/docs/Levelling-System/04-ROADMAP-IMPLEMENTAZIONE.md)  
   Milestone decision-complete per portare il sistema in produzione.

6. [05-RUNBOOK-OPERATIVO.md](/Users/patric/Documents/Minecraft/CittaEXP/docs/Levelling-System/05-RUNBOOK-OPERATIVO.md)  
   Checklist operativa, tuning e gestione incidenti.

7. [06-BACKLOG-EVENTI-E-STREAK.md](/Users/patric/Documents/Minecraft/CittaEXP/docs/Levelling-System/06-BACKLOG-EVENTI-E-STREAK.md)  
   Estensioni future (streak, eventi globali, capitale stagionale avanzata).

## Integrazione research
- Il catalogo sfide include già il pool esteso derivato da [extra-challenges.md](/Users/patric/Documents/Minecraft/CittaEXP/docs/research/extra-challenges.md).
- Il rollout operativo delle nuove categorie è allineato in [04-ROADMAP-IMPLEMENTAZIONE.md](/Users/patric/Documents/Minecraft/CittaEXP/docs/Levelling-System/04-ROADMAP-IMPLEMENTAZIONE.md).

## Runtime M1 (stato)
- Daily live: `3 standard + 2 race` (`16:00` e `21:00`, Europe/Rome).
- Reward engine: XP città + command console templated + item su vault città.
- Item Vault città (GUI 9x6) con ACL per-player `deposit/withdraw`.

## Runtime M2 (stato)
- Scheduler multi-ciclo live: `daily + weekly + monthly`.
- Profilo attivo: `3 daily standard + 2 race + 3 weekly + 2 monthly`.
- Rotazione deterministica globale per ciclo (`Europe/Rome`).
- GUI Sfide aggiornata con `ciclo/categoria/tempo residuo`.
