package net.dwhincandi.livingworld.story;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.dwhincandi.livingworld.LivingWorldDirector;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * World-wide campaign state for the single-player 0.1 prototype.
 * Every mutating method marks this SavedData dirty so Minecraft persists it.
 */
public final class WorldStoryData extends SavedData {
    private enum QuestStatus {
        NOT_STARTED("not_started", "Nicht angeboten"),
        OFFERED("offered", "Angeboten"),
        ACCEPTED("accepted", "Angenommen"),
        COMPLETED("completed", "Abgeschlossen"),
        IGNORED("ignored", "Ignoriert"),
        FAILED("failed", "Gescheitert");

        private final String id;
        private final String label;

        QuestStatus(String id, String label) {
            this.id = id;
            this.label = label;
        }

        private static QuestStatus fromId(String id) {
            for (QuestStatus status : values()) {
                if (status.id.equals(id)) {
                    return status;
                }
            }
            return NOT_STARTED;
        }
    }

    private static final Codec<QuestStatus> QUEST_STATUS_CODEC = Codec.STRING.xmap(
            QuestStatus::fromId,
            status -> status.id
    );

    private static final Codec<WorldStoryData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("campaign_chapter", 0).forGetter(data -> data.campaignChapter),
            Codec.INT.optionalFieldOf("threat", 0).forGetter(data -> data.threat),
            Codec.INT.optionalFieldOf("village_trust", 0).forGetter(data -> data.villageTrust),
            Codec.INT.optionalFieldOf("village_strength", 0).forGetter(data -> data.villageStrength),
            QUEST_STATUS_CODEC.optionalFieldOf("village_defense_status", QuestStatus.NOT_STARTED)
                    .forGetter(data -> data.villageDefenseStatus),
            Codec.INT.optionalFieldOf("village_x", 0).forGetter(data -> data.villageX),
            Codec.INT.optionalFieldOf("village_y", 0).forGetter(data -> data.villageY),
            Codec.INT.optionalFieldOf("village_z", 0).forGetter(data -> data.villageZ),
            Codec.INT.optionalFieldOf("successful_defenses", 0).forGetter(data -> data.successfulDefenses),
            Codec.INT.optionalFieldOf("ignored_defenses", 0).forGetter(data -> data.ignoredDefenses)
    ).apply(instance, WorldStoryData::new));

    private static final SavedDataType<WorldStoryData> TYPE = new SavedDataType<>(
            LivingWorldDirector.id("world_story"),
            WorldStoryData::new,
            CODEC,
            null
    );

    private int campaignChapter;
    private int threat;
    private int villageTrust;
    private int villageStrength;
    private QuestStatus villageDefenseStatus;
    private int villageX;
    private int villageY;
    private int villageZ;
    private int successfulDefenses;
    private int ignoredDefenses;

    public WorldStoryData() {
        this(0, 0, 0, 0, QuestStatus.NOT_STARTED, 0, 0, 0, 0, 0);
    }

    private WorldStoryData(
            int campaignChapter,
            int threat,
            int villageTrust,
            int villageStrength,
            QuestStatus villageDefenseStatus,
            int villageX,
            int villageY,
            int villageZ,
            int successfulDefenses,
            int ignoredDefenses
    ) {
        this.campaignChapter = Math.max(0, Math.min(5, campaignChapter));
        this.threat = clamp(threat, 0, 100);
        this.villageTrust = clamp(villageTrust, -10, 10);
        this.villageStrength = clamp(villageStrength, 0, 10);
        this.villageDefenseStatus = villageDefenseStatus == null
                ? QuestStatus.NOT_STARTED
                : villageDefenseStatus;
        this.villageX = villageX;
        this.villageY = villageY;
        this.villageZ = villageZ;
        this.successfulDefenses = Math.max(0, successfulDefenses);
        this.ignoredDefenses = Math.max(0, ignoredDefenses);
    }

    public static WorldStoryData get(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(ServerLevel.OVERWORLD);
        if (overworld == null) {
            return new WorldStoryData();
        }
        return overworld.getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean offerVillageDefense(BlockPos position) {
        if (villageDefenseStatus != QuestStatus.NOT_STARTED) {
            return false;
        }

        villageDefenseStatus = QuestStatus.OFFERED;
        villageX = position.getX();
        villageY = position.getY();
        villageZ = position.getZ();
        setDirty();
        return true;
    }

    public boolean acceptVillageDefense() {
        if (villageDefenseStatus != QuestStatus.OFFERED) {
            return false;
        }

        villageDefenseStatus = QuestStatus.ACCEPTED;
        setDirty();
        return true;
    }

    /** Resolves the prototype quest. Accepted quests can succeed or fail; offered quests can be ignored. */
    public boolean resolveVillageDefense(String outcome) {
        switch (outcome) {
            case "success" -> {
                if (villageDefenseStatus != QuestStatus.ACCEPTED) {
                    return false;
                }
                villageDefenseStatus = QuestStatus.COMPLETED;
                villageTrust = clamp(villageTrust + 2, -10, 10);
                villageStrength = clamp(villageStrength + 1, 0, 10);
                threat = clamp(threat - 1, 0, 100);
                successfulDefenses++;
            }
            case "ignored" -> {
                if (villageDefenseStatus != QuestStatus.OFFERED
                        && villageDefenseStatus != QuestStatus.ACCEPTED) {
                    return false;
                }
                villageDefenseStatus = QuestStatus.IGNORED;
                villageTrust = clamp(villageTrust - 1, -10, 10);
                villageStrength = clamp(villageStrength - 1, 0, 10);
                threat = clamp(threat + 1, 0, 100);
                ignoredDefenses++;
            }
            case "failed" -> {
                if (villageDefenseStatus != QuestStatus.ACCEPTED) {
                    return false;
                }
                villageDefenseStatus = QuestStatus.FAILED;
                villageTrust = clamp(villageTrust - 1, -10, 10);
                threat = clamp(threat + 1, 0, 100);
            }
            default -> {
                return false;
            }
        }

        // Both success and ignoring the offer move the story forward. Neither creates a softlock.
        campaignChapter = Math.max(campaignChapter, 1);
        setDirty();
        return true;
    }

    public void reset() {
        campaignChapter = 0;
        threat = 0;
        villageTrust = 0;
        villageStrength = 0;
        villageDefenseStatus = QuestStatus.NOT_STARTED;
        villageX = 0;
        villageY = 0;
        villageZ = 0;
        successfulDefenses = 0;
        ignoredDefenses = 0;
        setDirty();
    }

    public int getCampaignChapter() {
        return campaignChapter;
    }

    public int getThreat() {
        return threat;
    }

    public int getVillageTrust() {
        return villageTrust;
    }

    public int getVillageStrength() {
        return villageStrength;
    }

    public String getVillageDefenseStatusLabel() {
        return villageDefenseStatus.label;
    }

    public String getVillageDefenseStatusId() {
        return villageDefenseStatus.id;
    }

    public String getChapterLabel() {
        return campaignChapter == 0 ? "Alltag" : "Spuren";
    }

    public String getVillagePositionLabel() {
        return villageX + ", " + villageY + ", " + villageZ;
    }

    public int getSuccessfulDefenses() {
        return successfulDefenses;
    }

    public int getIgnoredDefenses() {
        return ignoredDefenses;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
