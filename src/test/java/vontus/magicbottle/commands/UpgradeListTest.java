package vontus.magicbottle.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;
import vontus.magicbottle.Upgrade;

class UpgradeListTest {
    @Test
    void parsesOneUpgrade() throws CommandSyntaxException {
        assertEquals(EnumSet.of(Upgrade.COLLECT), UpgradeList.parse("collect"));
    }

    @Test
    void parsesSeveralUpgradesAsASet() throws CommandSyntaxException {
        assertEquals(EnumSet.allOf(Upgrade.class), UpgradeList.parse("collect,autorepair"));
        assertEquals(EnumSet.allOf(Upgrade.class), UpgradeList.parse("autorepair,collect"));
    }

    @Test
    void rejectsUnknownIds() {
        assertThrows(CommandSyntaxException.class, () -> UpgradeList.parse("fly"));
        assertThrows(CommandSyntaxException.class, () -> UpgradeList.parse("collect,fly"));
        assertThrows(CommandSyntaxException.class, () -> UpgradeList.parse("Collect"));
    }

    @Test
    void rejectsRepeatedIds() {
        assertThrows(CommandSyntaxException.class, () -> UpgradeList.parse("collect,collect"));
    }

    @Test
    void rejectsEmptyIds() {
        assertThrows(CommandSyntaxException.class, () -> UpgradeList.parse(""));
        assertThrows(CommandSyntaxException.class, () -> UpgradeList.parse("collect,"));
        assertThrows(CommandSyntaxException.class, () -> UpgradeList.parse(",collect"));
    }
}
