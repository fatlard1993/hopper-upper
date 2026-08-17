package justfatlard.hopper_upper.integration;

import java.util.Random;
import justfatlard.hopper_upper.quest.LiftToTheLoftQuest;
import justfatlard.village_quests.api.QuestRegistry;
import justfatlard.village_quests.quest.VillagerQuest;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * Offers the loft problem from the people who store things.
 *
 * <p>Names village-quests types directly, so it must only be loaded behind the
 * isModLoaded guard in the entry point.
 */
public final class HopperQuestRegistration {
	private HopperQuestRegistration() {}

	private static final float OFFER_CHANCE = 0.12F;

	public static void register() {
		QuestRegistry.registerProfessionQuest("farmer", HopperQuestRegistration::offer);
		QuestRegistry.registerProfessionQuest("librarian", HopperQuestRegistration::offer);
	}

	private static VillagerQuest offer(Villager villager, String villagerName, int reputation, Random random) {
		if (reputation < 10) return null;
		if (random.nextFloat() > OFFER_CHANCE) return null;

		return new LiftToTheLoftQuest(villagerName, villager.getUUID(), villager.blockPosition());
	}
}
