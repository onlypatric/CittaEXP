# Prompt GPT-5.4 — Mob Invasion da 3 a 10 Tier

Usa questo prompt con `GPT-5.4` per produrre una proposta completa di espansione del sistema `Mob invasion`.

```text
Sto lavorando a un plugin Minecraft chiamato `CittaEXP`.

Ho un sistema di difesa cittadina chiamato `Mob invasion`.
Oggi il sistema ha solo 3 tier (`L1`, `L2`, `L3`), ma voglio portarlo ad almeno 10 tier totali.

Mi serve che tu produca una proposta **completa, concreta e implementabile** per espandere questo sistema.

Importante:
- Non voglio brainstorming.
- Non voglio spiegazioni generiche.
- Non voglio alternative multiple.
- Voglio una proposta unica, coerente, molto concreta, pronta da tradurre in implementazione.

## Contesto tecnico reale attuale

### Enum attuale
Il runtime oggi ha:

```java
public enum CityDefenseTier {
    L1,
    L2,
    L3;
}
```

### Spec runtime per ogni tier
Ogni tier oggi usa questi campi:

```java
public record CityDefenseLevelSpec(
        CityDefenseTier tier,
        int minCityLevel,
        int waves,
        double guardianHp,
        BigDecimal startCost,
        long cooldownSeconds,
        int rewardXp,
        Map<Material, Integer> rewardItems,
        int baseMobCount,
        int waveIncrement,
        int bossSupportCount,
        double eliteChance,
        List<EntityType> regularMobs,
        List<EntityType> eliteMobs,
        EntityType bossMob
) {
}
```

### File `defense.yml` attuale
I parametri globali attuali sono:

```yml
enabled: true
globalActiveCap: 3
antiCheeseRadius: 12
maxMobsAlive: 60
playerDeathPenaltyPercent: 0.10

timing:
  prepSeconds: 45
  interWaveSeconds: 10
  sessionTimeoutSeconds: 1200

spawn:
  minPoints: 4
  radiusMin: 18
  radiusMax: 26

defenderScaling:
  perExtraDefender: 0.25
  maxMultiplier: 2.5
```

### Tier attuali
```yml
levels:
  l1:
    minCityLevel: 3
    waves: 3
    guardianHp: 250
    startCost: 5000
    cooldownSeconds: 7200
    rewardXp: 150
    baseMobCount: 8
    waveIncrement: 3
    bossSupportCount: 6
    eliteChance: 0.10
    regularMobs:
      - ZOMBIE
      - SKELETON
      - SPIDER
    eliteMobs:
      - HUSK
      - STRAY
    bossMob: RAVAGER
    rewardItems:
      IRON_INGOT: 96
      BREAD: 64

  l2:
    minCityLevel: 6
    waves: 5
    guardianHp: 450
    startCost: 15000
    cooldownSeconds: 21600
    rewardXp: 350
    baseMobCount: 11
    waveIncrement: 4
    bossSupportCount: 8
    eliteChance: 0.14
    regularMobs:
      - ZOMBIE
      - SKELETON
      - SPIDER
      - CREEPER
    eliteMobs:
      - HUSK
      - STRAY
      - PILLAGER
    bossMob: RAVAGER
    rewardItems:
      IRON_INGOT: 128
      GOLD_INGOT: 48
      COOKED_BEEF: 64

  l3:
    minCityLevel: 10
    waves: 7
    guardianHp: 700
    startCost: 35000
    cooldownSeconds: 43200
    rewardXp: 800
    baseMobCount: 14
    waveIncrement: 5
    bossSupportCount: 12
    eliteChance: 0.18
    regularMobs:
      - ZOMBIE
      - SKELETON
      - SPIDER
      - CREEPER
      - PILLAGER
    eliteMobs:
      - VINDICATOR
      - WITHER_SKELETON
      - PIGLIN_BRUTE
    bossMob: WARDEN
    rewardItems:
      GOLD_INGOT: 128
      DIAMOND: 16
      COOKED_BEEF: 96
```

### UI attuale
La GUI mostra ancora solo:
- `Difesa L1`
- `Difesa L2`
- `Difesa L3`

Quindi se proponi 10 tier, devi considerare anche:
- espansione enum `CityDefenseTier`
- titoli GUI `l4..l10`
- eventuali etichette/titoli player-facing

## Obiettivo prodotto
Voglio una progressione a **10 tier totali**:
- `L1` fino a `L10`
- i nuovi tier devono essere **molto più duri**
- devono sembrare davvero una progressione da endgame cittadino
- non devono essere solo copie numericamente gonfiate
- devono avere una crescita sensata di:
  - wave
  - guardianHp
  - startCost
  - cooldownSeconds
  - rewardXp
  - rewardItems
  - baseMobCount
  - waveIncrement
  - bossSupportCount
  - eliteChance
  - qualità dei mob

## Preferenze forti
1. Voglio una proposta **unica**, non 3 alternative.
2. Voglio almeno `L4..L10`, ma se ritieni opportuno puoi anche ritoccare `L1..L3` per farli reggere meglio il nuovo sistema a 10 tier.
3. La crescita deve essere **molto più dura**, non conservativa.
4. Sei autorizzato a proporre anche modifiche ai parametri globali di `defense.yml` se servono davvero a sostenere 10 tier.
5. Voglio mantenere i nomi tecnici `L1..L10`, ma aggiungi anche un **epiteto tematico** per ogni tier da usare in GUI/copy.
6. I reward devono crescere in modo coerente con la difficoltà:
   - più XP
   - più valore item
   - item più rari nei tier alti
7. I mob devono evolvere in maniera sensata:
   - tier bassi: orde semplici
   - tier medi: pressione, ranged, disruption
   - tier alti: assedio duro, elite pericolose, boss davvero seri
8. Evita entità o combinazioni palesemente tossiche o bug-prone se non strettamente necessarie.
9. Voglio un sistema che lasci spazio a un futuro retiering/retheme, quindi niente design troppo rigido o mono-tema.
10. Non inventare nuovi campi nel record `CityDefenseLevelSpec`, a meno che sia davvero indispensabile; se proponi un nuovo campo, devi motivarlo molto brevemente.

## Output che voglio
Rispondi in 4 sezioni esatte, in questo ordine:

### 1. `Design Summary`
Breve sintesi del modello a 10 tier:
- come scala
- che ruolo hanno early/mid/late/endgame
- se hai ritoccato anche `L1..L3`

### 2. `Implementation Delta`
Elenco concreto e minimale di ciò che andrebbe cambiato nel codice/config:
- enum `CityDefenseTier`
- `defense.yml`
- titoli GUI/config `l4..l10`
- eventuali global settings ritoccati
- eventuali note su compatibilità

### 3. `YAML`
Voglio un blocco YAML completo e coerente, pronto da copiare in `defense.yml`.
Deve includere:
- eventuali global settings modificati
- `levels.l1` fino a `levels.l10`
- ogni tier con tutti i campi richiesti

### 4. `UI Labels`
Una sezione breve con:
- `L1..L10`
- epiteto tematico per ciascun tier
- titolo GUI consigliato per ciascun tier
Esempio stile:
- `l4: Difesa L4 — Bastione Cremisi`

## Vincoli di qualità
- Nessun placeholder tipo `TBD`
- Nessun commento superfluo
- Nessuna spiegazione lunga
- Niente pseudo-codice Java
- Niente markdown eccessivamente decorativo
- YAML coerente e plausibile
- I tier alti devono essere chiaramente più rari, costosi e impegnativi
- La proposta deve sembrare progettata per un survival MMO cittadino, non per un minigioco arcade

Ora produci la proposta completa.
```
