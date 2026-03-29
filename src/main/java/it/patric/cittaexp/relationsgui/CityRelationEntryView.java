package it.patric.cittaexp.relationsgui;

import java.util.UUID;
import net.william278.husktowns.town.Town;

public record CityRelationEntryView(
        int townId,
        String townName,
        UUID mayorId,
        String tag,
        String colorRgb,
        Town.Relation relation,
        Town.Relation reciprocalRelation
) {
}
