package vontus.magicbottle.config;

public class PermissionGroup {
	private String name;
	private int maxLevel;
	private boolean canRepairNormal;
	private boolean canRepairAuto;
	private int xpDepositCost;
	private int moneyCreateCost;
	private static String GROUP_PERMISSION_PREFIX = "magicbottle.group.";

	public PermissionGroup(String name, int maxLevel, boolean canRepairNormal, boolean canRepairAuto, int xpDepositCost, int moneyCreateCost) {
		this.name = name;
		this.maxLevel = maxLevel;
		this.canRepairNormal = canRepairNormal;
		this.canRepairAuto = canRepairAuto;
		this.xpDepositCost = xpDepositCost;
		this.moneyCreateCost = moneyCreateCost;
	}

	public int getMaxLevel() {
		return maxLevel;
	}

	public boolean canRepairNormal() {
		return canRepairNormal;
	}

	public boolean canRepairAuto() {
		return canRepairAuto;
	}

	public int getXpDepositCost() {
		return xpDepositCost;
	}

	public int getMoneyCreateCost() {
		return moneyCreateCost;
	}

	public String getPermissioN() {
		return GROUP_PERMISSION_PREFIX + name;
	}
}
