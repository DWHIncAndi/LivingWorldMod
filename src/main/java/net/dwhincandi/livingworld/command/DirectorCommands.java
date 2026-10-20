package net.dwhincandi.livingworld.command;

import net.dwhincandi.livingworld.story.WorldStoryData;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class DirectorCommands {
    private DirectorCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("ld")
                        .executes(context -> showStatus(context.getSource()))
                        .then(Commands.literal("status")
                                .executes(context -> showStatus(context.getSource())))
                        .then(Commands.literal("offer")
                                .then(Commands.literal("village_defense")
                                        .executes(context -> offerVillageDefense(context.getSource()))))
                        .then(Commands.literal("accept")
                                .then(Commands.literal("village_defense")
                                        .executes(context -> acceptVillageDefense(context.getSource()))))
                        .then(Commands.literal("resolve")
                                .then(Commands.literal("village_defense")
                                        .then(Commands.literal("success")
                                                .executes(context -> resolveVillageDefense(context.getSource(), "success")))
                                        .then(Commands.literal("ignored")
                                                .executes(context -> resolveVillageDefense(context.getSource(), "ignored")))
                                        .then(Commands.literal("failed")
                                                .executes(context -> resolveVillageDefense(context.getSource(), "failed")))))
                        .then(Commands.literal("reset-world")
                                .executes(context -> resetWorld(context.getSource())))));
    }

    private static int showStatus(net.minecraft.commands.CommandSourceStack source) {
        WorldStoryData data = WorldStoryData.get(source.getServer());
        source.sendSuccess(() -> Component.literal("[Living World Director] Version 0.1"), false);
        source.sendSuccess(() -> Component.literal("Kapitel: " + data.getChapterLabel()), false);
        source.sendSuccess(() -> Component.literal("Bedrohung: " + data.getThreat() + "/100"), false);
        source.sendSuccess(() -> Component.literal("Dorfvertrauen: " + data.getVillageTrust() + "/10"), false);
        source.sendSuccess(() -> Component.literal("Dorfstärke: " + data.getVillageStrength() + "/10"), false);
        source.sendSuccess(() -> Component.literal("Dorfverteidigung: " + data.getVillageDefenseStatusLabel()), false);
        source.sendSuccess(() -> Component.literal("Quest-Ort: " + data.getVillagePositionLabel()), false);
        source.sendSuccess(() -> Component.literal("Erfolgreiche Verteidigungen: " + data.getSuccessfulDefenses()
                + " | Ignorierte Angebote: " + data.getIgnoredDefenses()), false);
        return 1;
    }

    private static int offerVillageDefense(net.minecraft.commands.CommandSourceStack source) {
        WorldStoryData data = WorldStoryData.get(source.getServer());
        BlockPos position = BlockPos.containing(source.getPosition());
        if (!data.offerVillageDefense(position)) {
            source.sendFailure(Component.literal("Das Dorfverteidigungs-Angebot wurde bereits verwendet. Nutze /ld reset-world für einen neuen Test."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Neues optionales Story-Angebot: Ein Grenzdorf braucht Hilfe."), false);
        source.sendSuccess(() -> Component.literal("Prototyp-Zielposition gespeichert: "
                + position.getX() + ", " + position.getY() + ", " + position.getZ()), false);
        source.sendSuccess(() -> Component.literal("Annehmen: /ld accept village_defense | Ignorieren: /ld resolve village_defense ignored"), false);
        return 1;
    }

    private static int acceptVillageDefense(net.minecraft.commands.CommandSourceStack source) {
        WorldStoryData data = WorldStoryData.get(source.getServer());
        if (!data.acceptVillageDefense()) {
            source.sendFailure(Component.literal("Es gibt kein offenes Dorfverteidigungs-Angebot, das angenommen werden kann."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Du hast die Dorfverteidigung angenommen. Teste nun Erfolg oder Scheitern."), false);
        source.sendSuccess(() -> Component.literal("/ld resolve village_defense success  |  /ld resolve village_defense failed"), false);
        return 1;
    }

    private static int resolveVillageDefense(net.minecraft.commands.CommandSourceStack source, String outcome) {
        WorldStoryData data = WorldStoryData.get(source.getServer());
        if (!data.resolveVillageDefense(outcome)) {
            source.sendFailure(Component.literal("Dieser Ausgang passt nicht zum aktuellen Quest-Zustand. Prüfe /ld status."));
            return 0;
        }

        switch (outcome) {
            case "success" -> {
                source.sendSuccess(() -> Component.literal("Das Dorf erinnert sich an deine Hilfe. Vertrauen +2, Dorfstärke +1, Bedrohung -1."), false);
                Entity entity = source.getEntity();
                if (entity instanceof ServerPlayer player) {
                    player.getInventory().add(new ItemStack(Items.EMERALD, 3));
                    source.sendSuccess(() -> Component.literal("Belohnung: 3 Smaragde (falls Inventarplatz vorhanden)."), false);
                }
            }
            case "ignored" -> source.sendSuccess(() -> Component.literal(
                    "Du hast das Angebot ignoriert. Die Geschichte geht weiter, aber das Dorf verliert Vertrauen und Stärke."), false);
            case "failed" -> source.sendSuccess(() -> Component.literal(
                    "Die Verteidigung ist gescheitert. Das Dorf vertraut dir weniger und die Bedrohung steigt."), false);
            default -> throw new IllegalStateException("Unexpected outcome: " + outcome);
        }

        source.sendSuccess(() -> Component.literal("Kapitelwechsel: Spuren. Die Konsequenz wurde gespeichert."), false);
        return 1;
    }

    private static int resetWorld(net.minecraft.commands.CommandSourceStack source) {
        WorldStoryData.get(source.getServer()).reset();
        source.sendSuccess(() -> Component.literal("Die gespeicherten Story-Daten dieser Welt wurden zurückgesetzt."), false);
        return 1;
    }
}
