package justfatlard.hopper_upper.quest;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import justfatlard.hopper_upper.Main;
import justfatlard.village_quests.quest.VillagerQuest;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Something that carries goods upward.
 *
 * <p>Hoppers go down. Everyone knows hoppers go down, which is exactly why
 * nobody ever tries to place one facing up, and why a mod that lets you do it
 * can sit installed for a year without being noticed. There is no recipe to
 * find and no item to hold: the discovery is entirely in where you point it.
 *
 * <p>So a farmer wants grain moved to a loft, which is the one problem a hopper
 * has never been able to solve, and the answer is the thing nobody tries.
 */
public class LiftToTheLoftQuest extends VillagerQuest {
	/** Cheap enough to run when a dialogue opens, wide enough to cover a barn. */
	private static final int SEARCH = 6;

	private final BlockPos near;

	public LiftToTheLoftQuest(String requesterName, UUID villagerUuid, BlockPos near) {
		super(VillagerQuest.QuestType.CREATION, requesterName, villagerUuid, 6);
		this.near = near.immutable();
	}

	@Override
	public String getDescription() {
		ThreadLocalRandom rng = ThreadLocalRandom.current();
		String[] lines = {
			this.requesterName + ": \"The grain has to go up to the loft and I have carried it up a ladder every harvest "
				+ "of my life. Somebody told me a hopper can be made to point upward. I did not believe them.\"",
			this.requesterName + ": \"Hoppers go down. That is what hoppers do. But I have heard - and I would like proof - "
				+ "that one can be set to lift instead. Build me one and I will stop climbing.\"",
			this.requesterName + ": \"Everything in this village falls downward and my storage is upstairs. "
				+ "Put me a hopper in that carries the other way.\""
		};
		return lines[rng.nextInt(lines.length)];
	}

	@Override
	public String getObjective() {
		return "build an upward hopper near " + this.requesterName
			+ " - aim a hopper at the block above it and it will lift instead of drop";
	}

	@Override
	public boolean checkCompletion(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel world)) return false;

		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int dx = -SEARCH; dx <= SEARCH; dx++) {
			for (int dy = -SEARCH; dy <= SEARCH; dy++) {
				for (int dz = -SEARCH; dz <= SEARCH; dz++) {
					cursor.set(this.near.getX() + dx, this.near.getY() + dy, this.near.getZ() + dz);
					if (!world.isLoaded(cursor)) continue;

					if (world.getBlockState(cursor).is(Main.UPWARD_HOPPER_BLOCK)) return true;
				}
			}
		}
		return false;
	}

	@Override
	public void onComplete(ServerPlayer player) {
		// It stays where it is. It is doing a job.
	}
}
