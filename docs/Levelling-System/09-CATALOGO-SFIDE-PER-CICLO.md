# Catalogo Sfide Spawnabili per Ciclo (Runtime Attuale)

Fonte: generatore procedurale code-first (`ProceduralChallengeGenerator.defaultProfiles()`), stato aggiornato a oggi.

## Snapshot cicli
- Daily: `3` standard + `2` race (race hidden fino alla reveal)
- Weekly: `3` missioni
- Monthly: `1` missione
- Monthly Event: `6` missioni per finestra (`A` e `B`)

## Daily Standard
- `proc_resources` — Contributo Risorse (`RESOURCE_CONTRIBUTION`)
- `proc_mining` — Mining (`BLOCK_MINE`) — variante globale/specifica su minerali
- `proc_mob_hunt` — Caccia ai Mob (`MOB_KILL`) — variante globale/specifica su mob
- `proc_rare_mob` — Mob Rari (`RARE_MOB_KILL`) — specifica su mob rari
- `proc_crop` — Raccolto (`CROP_HARVEST`) — variante globale/specifica su colture
- `proc_fishing` — Pesca (`FISH_CATCH`)
- `proc_transport` — Distanza Trasporto (`TRANSPORT_DISTANCE`)
- `proc_playtime` — Tempo Online (`PLAYTIME_MINUTES`)
- `proc_xp_pickup` — Raccolta XP (`XP_PICKUP`)
- `proc_construction` — Costruzione (`CONSTRUCTION`) — variante globale/specifica materiali
- `proc_redstone` — Automazione Redstone (`REDSTONE_AUTOMATION`) — variante globale/specifica
- `proc_animals` — Interazione Animali (`ANIMAL_INTERACTION`) — variante globale/specifica
- `proc_villager_trade` — Scambi Villager (`VILLAGER_TRADE`)
- `proc_dimension` — Viaggio Dimensioni (`DIMENSION_TRAVEL`)
- `proc_archaeology` — Archeologia (`ARCHAEOLOGY_BRUSH`)
- `proc_economy` — Economia Avanzata (`ECONOMY_ADVANCED`)

## Daily Race (16:00 / 21:00)
- `proc_mob_hunt` — Caccia ai Mob (`MOB_KILL`)

Nota: al momento la race giornaliera è volutamente focalizzata su una sola famiglia (mob hunt).

## Weekly Standard
- `proc_resources` — Contributo Risorse (`RESOURCE_CONTRIBUTION`)
- `proc_mining` — Mining (`BLOCK_MINE`)
- `proc_mob_hunt` — Caccia ai Mob (`MOB_KILL`)
- `proc_rare_mob` — Mob Rari (`RARE_MOB_KILL`)
- `proc_crop` — Raccolto (`CROP_HARVEST`)
- `proc_fishing` — Pesca (`FISH_CATCH`)
- `proc_transport` — Distanza Trasporto (`TRANSPORT_DISTANCE`)
- `proc_playtime` — Tempo Online (`PLAYTIME_MINUTES`)
- `proc_xp_pickup` — Raccolta XP (`XP_PICKUP`)
- `proc_structure_discovery` — Esplorazione (`STRUCTURE_DISCOVERY`)
- `proc_construction` — Costruzione (`CONSTRUCTION`)
- `proc_redstone` — Automazione Redstone (`REDSTONE_AUTOMATION`)
- `proc_animals` — Interazione Animali (`ANIMAL_INTERACTION`)
- `proc_villager_trade` — Scambi Villager (`VILLAGER_TRADE`)
- `proc_dimension` — Viaggio Dimensioni (`DIMENSION_TRAVEL`)
- `proc_archaeology` — Archeologia (`ARCHAEOLOGY_BRUSH`)
- `proc_economy` — Economia Avanzata (`ECONOMY_ADVANCED`)

## Monthly Standard
- `proc_mining` — Mining (`BLOCK_MINE`)
- `proc_crop` — Raccolto (`CROP_HARVEST`)
- `proc_transport` — Distanza Trasporto (`TRANSPORT_DISTANCE`)
- `proc_playtime` — Tempo Online (`PLAYTIME_MINUTES`)
- `proc_structure_discovery` — Esplorazione (`STRUCTURE_DISCOVERY`)
- `proc_boss` — Boss (`BOSS_KILL`)

## Monthly Event (Window A/B)
- `proc_mining` — Mining (`BLOCK_MINE`)
- `proc_mob_hunt` — Caccia ai Mob (`MOB_KILL`)
- `proc_rare_mob` — Mob Rari (`RARE_MOB_KILL`)
- `proc_crop` — Raccolto (`CROP_HARVEST`)
- `proc_transport` — Distanza Trasporto (`TRANSPORT_DISTANCE`)
- `proc_playtime` — Tempo Online (`PLAYTIME_MINUTES`)
- `proc_structure_discovery` — Esplorazione (`STRUCTURE_DISCOVERY`)
- `proc_construction` — Costruzione (`CONSTRUCTION`)
- `proc_redstone` — Automazione Redstone (`REDSTONE_AUTOMATION`)
- `proc_animals` — Interazione Animali (`ANIMAL_INTERACTION`)
- `proc_villager_trade` — Scambi Villager (`VILLAGER_TRADE`)
- `proc_dimension` — Viaggio Dimensioni (`DIMENSION_TRAVEL`)
- `proc_archaeology` — Archeologia (`ARCHAEOLOGY_BRUSH`)
- `proc_economy` — Economia Avanzata (`ECONOMY_ADVANCED`)
- `proc_boss` — Boss (`BOSS_KILL`)

## Range Quantitativi Indicativi (per quality check)

Note:
- Range indicativi calcolati con i moltiplicatori ciclo attuali (`daily 0.55x`, con eccezione `mining/resource` a `0.275x`; `race 0.75x`; `weekly 1.00x`; `monthly 2.00x`; `event 2.40x`).
- I range qui sotto sono “nominali” (prima di eventuale fairness per-city e prima di eventuale scaling focus specifico common/uncommon/rare).
- Le varianti `SPECIFIC` possono abbassare il target (tipicamente `0.8x` o `0.6x` prima del moltiplicatore ciclo).
- Per `Mining` e `Contributo risorse`, alcuni materiali rari valgono `x2` o `x3` progresso per singola azione/item.

### Daily Standard — target indicativi
| Sfida | Range |
|---|---:|
| Contributo risorse | `60 - 140` |
| Mining | `70 - 190` |
| Caccia ai mob | `40 - 120` |
| Mob rari | `2 - 8` |
| Raccolto | `40 - 130` |
| Pesca | `10 - 50` |
| Distanza trasporto | `150 - 550` |
| Tempo online | `10 - 70` min |
| Raccolta XP | `80 - 600` |
| Costruzione | `50 - 190` |
| Automazione redstone | `12 - 72` |
| Interazione animali | `10 - 44` |
| Scambi villager | `4 - 22` |
| Viaggio dimensioni | `2 - 7` |
| Archeologia | `2 - 10` |
| Economia avanzata | `3 - 17` |

### Daily Race — target indicativi
| Sfida | Range |
|---|---:|
| Caccia ai mob | `60 - 170` |

### Weekly Standard — target indicativi
| Sfida | Range |
|---|---:|
| Contributo risorse | `220 - 520` |
| Mining | `260 - 700` |
| Caccia ai mob | `80 - 220` |
| Mob rari | `3 - 14` |
| Raccolto | `70 - 240` |
| Pesca | `18 - 95` |
| Distanza trasporto | `250 - 1000` |
| Tempo online | `20 - 120` min |
| Raccolta XP | `180 - 1100` |
| Esplorazione strutture | `1 - 3` |
| Costruzione | `90 - 350` |
| Automazione redstone | `24 - 130` |
| Interazione animali | `18 - 80` |
| Scambi villager | `8 - 40` |
| Viaggio dimensioni | `3 - 12` |
| Archeologia | `3 - 18` |
| Economia avanzata | `5 - 30` |

### Monthly Standard — target indicativi
| Sfida | Range |
|---|---:|
| Mining | `520 - 1400` |
| Raccolto | `140 - 480` |
| Distanza trasporto | `500 - 2000` |
| Esplorazione strutture | `2 - 6` |
| Boss | `2 - 4` |
| Tempo online | formula dinamica (vedi nota sotto) |

Nota playtime mensile: base per-town `N membri * 90 minuti`, varianza `±10%` a step `10`, poi moltiplicatore monthly `2.00x`.

### Monthly Event (A/B) — target indicativi
| Sfida | Range |
|---|---:|
| Mining | `620 - 1680` |
| Caccia ai mob | `190 - 530` |
| Mob rari | `7 - 34` |
| Raccolto | `170 - 580` |
| Distanza trasporto | `600 - 2400` |
| Esplorazione strutture | `2 - 7` |
| Costruzione | `220 - 840` |
| Automazione redstone | `60 - 312` |
| Interazione animali | `44 - 192` |
| Scambi villager | `20 - 96` |
| Viaggio dimensioni | `7 - 29` |
| Archeologia | `7 - 43` |
| Economia avanzata | `12 - 72` |
| Boss | `2 - 5` |
| Tempo online | formula dinamica (vedi nota sotto) |

Nota playtime evento mensile: base per-town `N membri * 90 minuti`, varianza `±10%` a step `10`, poi moltiplicatore event `2.40x`.

## Focus specifici possibili (solo dove previsto)
- Mining: `COAL_ORE`, `IRON_ORE`, `COPPER_ORE`, `REDSTONE_ORE`, `LAPIS_ORE`, `GOLD_ORE`, `DIAMOND_ORE`
- Mob hunt: `ZOMBIE`, `SKELETON`, `SPIDER`, `CREEPER`, `ENDERMAN`, `BLAZE`
- Rare mob: `BREEZE`, `PIGLIN_BRUTE`, `SHULKER`, `ELDER_GUARDIAN`, `WARDEN`
- Crop: `WHEAT`, `CARROTS`, `POTATOES`, `BEETROOTS`, `NETHER_WART`
- Construction: `STONE_BRICKS`, `BRICKS`, `OAK_PLANKS`, `DEEPSLATE_BRICKS`, `QUARTZ_BLOCK`
- Redstone: `REDSTONE`, `REDSTONE_TORCH`, `REPEATER`, `COMPARATOR`, `OBSERVER`, `HOPPER`
- Animals: `COW`, `SHEEP`, `PIG`, `CHICKEN`, `HORSE`, `WOLF`
