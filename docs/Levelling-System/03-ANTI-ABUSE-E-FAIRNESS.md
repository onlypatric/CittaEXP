# 03 - Anti-Abuse e Fairness

## Obiettivo
Impedire progressione artificiale (farm loop, alt, AFK) senza penalizzare il gameplay legittimo.

## Guardrail prioritari

### 1) Anti block-farm
- Problema: piazza/rompi loop infinito.
- Regola:
  - contare solo blocchi naturali, oppure
  - mantenere `placedBlocks` e ignorare i blocchi piazzati da player.
- Fallback semplice: pesare soprattutto ores/materiali non trivially farmabili.

### 2) Anti mob-farm
- Regola preferita: contare kill solo con spawn reason naturale.
- Ignorare kill da `SPAWNER`, `SPAWN_EGG`, `CUSTOM`.
- Aggiungere cap contributo kill per player/challenge.

### 3) Anti alt-account
- Cap max contributo per player (es. 30-40% del target sfida).
- Tracking per UUID unico.

### 4) Cooldown eventi
- Throttle per player (es. 1 progresso ogni 500 ms) per limitare spam.

### 5) Diminishing returns
- Esempio:
  - azioni 1-100 = 100%
  - 101-300 = 50%
  - 301+ = 20%
- Impatto: riduce farm estreme di un singolo utente.

### 6) Limiti giornalieri città
- Cap giornaliero per categoria (mining/mob/farming) per allungare la durata delle sfide.

### 7) Anti-AFK
- Se player immobile oltre soglia (es. 2 minuti), non accumula progresso attività.

### 8) Mix categorie obbligatorio
- Weekly composta da categorie diverse (mining + mob + farming).
- Evita meta mono-farm.

### 9) Randomizzazione pool
- Selezione casuale deterministica da pool più ampio (es. 3 su 15).
- Riduce ottimizzazioni statiche.

## Trasparenza interna città
- Mostrare sempre:
  - progresso totale città,
  - top contributor (top 3),
  - quota individuale.
- Beneficio: competizione sana e controllo sociale anti-abuso.

## Guardrail extra (pool esteso research)

### 10) Structure loot anti-reset
- Problema: loot farmato con reset strutture o cicli anomali.
- Regola:
  - contare aperture vault/chest con dedupe per posizione + finestra temporale,
  - cap giornaliero per tipo struttura.

### 11) Travel/biome anti-AFK
- Problema: movimento simulato/loop AFK.
- Regola:
  - contare distanza solo con delta reale e tempo minimo attivo,
  - ignorare pattern ciclici brevi nello stesso corridoio.

### 12) Economy/trade anti-loop
- Problema: trade ripetitivi non significativi.
- Regola:
  - diminishing returns su stesso villager/professione,
  - cap progress da una singola sorgente economica.

### 13) Redstone/automation anti-spoof
- Problema: item farmati con loop artificiali triviali.
- Regola:
  - contare output con throttle e soglia minima per intervallo,
  - dedupe eventi seriali troppo ravvicinati.
