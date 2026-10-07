package vontus.magicbottle;

/**
 * The upgrades a bottle can carry. The id is what is persisted in the bottle's PDC (and names its config section,
 * permission and recipe), so it must never change. They are listed in the order their lore lines are shown.
 */
public enum Upgrade {
	AUTO_REPAIR("autorepair"),
	COLLECT("collect");

	private final String id;

	Upgrade(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	/** The permission needed to apply it in the smithing table. */
	public String permission() {
		return "magicbottle.upgrade." + id();
	}

	public static Upgrade fromId(String id) {
		for (Upgrade upgrade : values()) {
			if (upgrade.id().equals(id)) {
				return upgrade;
			}
		}
		return null;
	}
}
