package it.patric.cittaexp.shop;

import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class CityShopFormatParserTest {

    @Test
    void parsesBuyOnlyPrice() {
        Optional<CityShopFormatParser.PriceSpec> parsed = CityShopFormatParser.parsePrices("B10K");
        Assertions.assertTrue(parsed.isPresent());
        Assertions.assertEquals(10_000L, parsed.get().buyPrice());
        Assertions.assertNull(parsed.get().sellPrice());
    }

    @Test
    void parsesSellOnlyPrice() {
        Optional<CityShopFormatParser.PriceSpec> parsed = CityShopFormatParser.parsePrices("S8");
        Assertions.assertTrue(parsed.isPresent());
        Assertions.assertNull(parsed.get().buyPrice());
        Assertions.assertEquals(8L, parsed.get().sellPrice());
    }

    @Test
    void parsesCombinedPrice() {
        Optional<CityShopFormatParser.PriceSpec> parsed = CityShopFormatParser.parsePrices("B10K : S8");
        Assertions.assertTrue(parsed.isPresent());
        Assertions.assertEquals(10_000L, parsed.get().buyPrice());
        Assertions.assertEquals(8L, parsed.get().sellPrice());
        Assertions.assertEquals("B10K : S8", CityShopFormatParser.formatPriceLine(parsed.get()));
    }

    @Test
    void rejectsDuplicateSide() {
        Assertions.assertTrue(CityShopFormatParser.parsePrices("B10 : B20").isEmpty());
    }

    @Test
    void rejectsMalformedToken() {
        Assertions.assertTrue(CityShopFormatParser.parsePrices("B10 : X3").isEmpty());
        Assertions.assertTrue(CityShopFormatParser.parsePrices("10K").isEmpty());
    }

    @Test
    void parsesQuantity() {
        Assertions.assertEquals(16, CityShopFormatParser.parseTradeQuantity("Q:16").orElseThrow());
        Assertions.assertEquals("Q:16", CityShopFormatParser.formatQuantityLine(16));
    }

    @Test
    void rejectsInvalidQuantity() {
        Assertions.assertTrue(CityShopFormatParser.parseTradeQuantity("Q:0").isEmpty());
        Assertions.assertTrue(CityShopFormatParser.parseTradeQuantity("Q:abc").isEmpty());
    }
}
