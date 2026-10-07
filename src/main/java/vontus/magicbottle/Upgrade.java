package vontus.magicbottle;

import java.util.Locale;

/**
 * The upgrades a bottle can carry. The id is what is persisted in the bottle's PDC, so it must never change. They are
 * listed in the order their lore lines are shown.
 */
public enum Upgrade {
	REPAIR;

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** The permission needed to apply it in the smithing table. */
	public String permission() {
		return "magicbottle.upgrade." + id();
	}

	static Upgrade fromId(String id) {
		for (Upgrade upgrade : values()) {
			if (upgrade.id().equals(id)) {
				return upgrade;
			}
		}
		return null;
	}
}
