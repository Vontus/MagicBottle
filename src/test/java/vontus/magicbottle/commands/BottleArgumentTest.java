package vontus.magicbottle.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;
import vontus.magicbottle.Upgrade;
import vontus.magicbottle.commands.BottleArgument.Spec;
import vontus.magicbottle.config.Config;

class BottleArgumentTest {
    private static Spec parse(String text) throws CommandSyntaxException {
        StringReader reader = new StringReader(text);
        Spec spec = new BottleArgument().parse(reader);
        assertEquals(text.length(), reader.getCursor(), "the whole argument is consumed");
        return spec;
    }

    @Test
    void parsesALevel() throws CommandSyntaxException {
        assertEquals(new Spec(30, EnumSet.noneOf(Upgrade.class), 1), parse("30"));
    }

    @Test
    void parsesTheLimitsOfTheLevel() throws CommandSyntaxException {
        assertEquals(0, parse("0").level());
        assertEquals(Config.maxLevel, parse(String.valueOf(Config.maxLevel)).level());
    }

    @Test
    void parsesUpgrades() throws CommandSyntaxException {
        assertEquals(new Spec(30, EnumSet.of(Upgrade.COLLECT), 1), parse("30[collect]"));
        assertEquals(new Spec(30, EnumSet.allOf(Upgrade.class), 1), parse("30[collect,autorepair]"));
    }

    @Test
    void parsesTheCountAfterASpace() throws CommandSyntaxException {
        assertEquals(new Spec(30, EnumSet.noneOf(Upgrade.class), 64), parse("30 64"));
        assertEquals(new Spec(30, EnumSet.of(Upgrade.COLLECT), 3), parse("30[collect] 3"));
        assertEquals(6400, parse("1 6400").count());
    }

    @Test
    void rejectsLevelsOutOfRange() {
        assertThrows(CommandSyntaxException.class, () -> parse("-1"));
        assertThrows(CommandSyntaxException.class, () -> parse(String.valueOf(Config.maxLevel + 1)));
        assertThrows(CommandSyntaxException.class, () -> parse("99999999999"));
    }

    @Test
    void rejectsAWrongLevel() {
        assertThrows(CommandSyntaxException.class, () -> parse(""));
        assertThrows(CommandSyntaxException.class, () -> parse("abc"));
        assertThrows(CommandSyntaxException.class, () -> parse("1.5"));
        assertThrows(CommandSyntaxException.class, () -> parse("[collect]"));
    }

    @Test
    void rejectsWrongUpgrades() {
        assertThrows(CommandSyntaxException.class, () -> parse("30[fly]"));
        assertThrows(CommandSyntaxException.class, () -> parse("30[collect,collect]"));
        assertThrows(CommandSyntaxException.class, () -> parse("30[collect"));
        assertThrows(CommandSyntaxException.class, () -> parse("30[collect,]"));
    }

    @Test
    void rejectsAWrongCount() {
        assertThrows(CommandSyntaxException.class, () -> parse("30 0"));
        assertThrows(CommandSyntaxException.class, () -> parse("30 6401"));
        assertThrows(CommandSyntaxException.class, () -> parse("30 -1"));
        assertThrows(CommandSyntaxException.class, () -> parse("30 many"));
        assertThrows(CommandSyntaxException.class, () -> parse("30 "));
    }
}
