package it.patric.cittaexp.cityoverlaytest;

import java.util.List;
import org.bukkit.Material;

public final class StaticOverlayTestDataProvider implements OverlayTestDataProvider {

    private final OverlayTestDataset dataset;

    public StaticOverlayTestDataProvider() {
        this.dataset = new OverlayTestDataset(
                "Aurelia",
                "Villaggio II",
                27,
                12_450,
                38_250.0,
                List.of(
                        new OverlayMissionCard(
                                "daily",
                                "Giornaliera",
                                "Caccia mineraria",
                                "Rompi 180 blocchi minerari utili.",
                                Material.IRON_PICKAXE,
                                OverlayMissionStatus.IN_PROGRESS,
                                46,
                                130,
                                22,
                                List.of("22 XP", "32x Coal")
                        ),
                        new OverlayMissionCard(
                                "race",
                                "Race giornaliera",
                                "Assalto mob",
                                "Sconfiggi 120 mob ostili.",
                                Material.IRON_SWORD,
                                OverlayMissionStatus.LOCKED,
                                0,
                                95,
                                50,
                                List.of("50 XP", "64x Iron Ingot")
                        ),
                        new OverlayMissionCard(
                                "weekly",
                                "Settimanale",
                                "Produzione alimentare",
                                "Produci 450 razioni utili alla citta.",
                                Material.BREAD,
                                OverlayMissionStatus.AVAILABLE,
                                8,
                                2_560,
                                58,
                                List.of("58 XP", "128x Bread")
                        ),
                        new OverlayMissionCard(
                                "monthly",
                                "Mensile",
                                "Monumento comune",
                                "Posiziona 7.500 blocchi per il progetto.",
                                Material.BRICKS,
                                OverlayMissionStatus.IN_PROGRESS,
                                22,
                                18_700,
                                112,
                                List.of("112 XP", "1x Beacon")
                        ),
                        new OverlayMissionCard(
                                "event",
                                "Evento",
                                "Corona mensile",
                                "Completa 5 task evento prima delle altre citta.",
                                Material.NETHER_STAR,
                                OverlayMissionStatus.AVAILABLE,
                                12,
                                3_420,
                                140,
                                List.of("140 XP", "Bundle ricompense evento")
                        ),
                        new OverlayMissionCard(
                                "seasonal",
                                "Stagionale",
                                "Fase squadra I",
                                "Raccogli, costruisci e sconfiggi il boss finale.",
                                Material.ENCHANTED_GOLDEN_APPLE,
                                OverlayMissionStatus.CLAIMABLE,
                                100,
                                32_400,
                                175,
                                List.of("175 XP", "Reward bundle stagionale")
                        )
                ),
                List.of(
                        "Patric", "Luna", "Aron", "Nami", "Elios", "Vex", "Dori", "Kora", "Milo", "Sora", "Nox", "Iris"
                ),
                List.of(
                        "Aurelia", "Rivaforte", "Nordheim", "Granporto", "Valdoro", "Eterna", "Pietralta", "Fjorden",
                        "Lunacava", "RoccaBlu", "Arenaria", "Silvarum", "Mistral", "Torrevia", "Solaria", "Noctis",
                        "Ghiacciaio", "Brughiera", "Argentia", "Corallia", "Drakken", "Rosaspina", "Bastione", "Tempesta"
                )
        );
    }

    @Override
    public OverlayTestDataset snapshot() {
        return dataset;
    }
}
