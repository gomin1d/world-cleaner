package ua.lokha.worldcleaner;

import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import ua.lokha.megachunkfixer2000.Utils;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class Main extends JavaPlugin {

    private Map<File, CleanThread> cleanMap = new HashMap<>();
    private long inhabitedTimeThresholdTicks = 1200;

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        this.loadConfigParams();
        this.getCommand("worldcleaner").setExecutor(this);
    }

    public void loadConfigParams() {
        inhabitedTimeThresholdTicks = this.getConfig().getLong("inhabitedTimeThresholdTicks", 1200);
    }

    @SuppressWarnings("ConstantConditions")
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§e==========[WorldCleaner]==========" +
                    "\n§4/worldcleaner reload §7- перезагрузить конфиг" +
                    "\n§4/worldcleaner listclean §7- список запущенных процессов очистки мира" +
                    "\n§4/worldcleaner startclean [world]/all §7- запустить процесс очистки мира, если указан all, то очищаются все миры" +
                    "\n§4/worldcleaner stopclean [world]/all §7- остановить процесс очистки мира, если указан all, то останавливаются все процессы");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            this.reloadConfig();
            this.loadConfigParams();
            sender.sendMessage("§aКонфиг перезагружен.");
            return true;
        }

        if (args[0].equalsIgnoreCase("listclean")) {
            sender.sendMessage("§eПроцессы очистки миров: ");
            for (CleanThread thread : cleanMap.values()) {
                sender.sendMessage("§7 - " + thread.getWorld().getName() + ": удалено чанков " + thread.getDeletedTotal() + "/" + thread.getCheckTotal() +
                        ", сократили размер мира " +
                        "с " + Utils.toLogLength(thread.getBeforeCleanUsedTotal()) + " " +
                        "до " + Utils.toLogLength(thread.getAfterCleanUsedTotal()) + " " +
                        "(-" + Utils.toLogPercent(thread.getAfterCleanUsedTotal(), thread.getBeforeCleanUsedTotal()) + "%).");
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("startclean")) {
            if (args.length < 2) {
                sender.sendMessage("§cУкажите имя мира или all.");
                return true;
            }

            List<File> worlds = new ArrayList<>();
            List<File> allWorlds = new ArrayList<>();
            String worldName = args[1];
            for (File world : Bukkit.getWorldContainer().listFiles(File::isDirectory)) {
                File regionFir = new File(world, "region");
                if (regionFir.exists() && regionFir.isDirectory()) {
                    allWorlds.add(world);
                }
            }
            if (worldName.equalsIgnoreCase("all")) {
                worlds.addAll(allWorlds);
            } else {
                File world = new File(Bukkit.getWorldContainer(), worldName);
                if (!world.exists() || !world.isDirectory()) {
                    sender.sendMessage("§cМир " + worldName + " не найден, список миров: " +
                            allWorlds.stream().map(File::getName).collect(Collectors.joining(", ")));
                    return true;
                }

                worlds.add(world);
            }

            for (File world : worlds) {
                if (cleanMap.containsKey(world)) {
                    sender.sendMessage("§eДля мира " + world.getName() + " уже запущен процесс очистки мира, подробнее /worldcleaner listclean");
                    continue;
                }

                CleanThread thread = new CleanThread(world, this);
                thread.start();
                cleanMap.put(world, thread);
                sender.sendMessage("§aЗапустили процесс очистки мира " + world.getName() + ", подробности будут в консоли.");
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("stopclean")) {
            if (args.length < 2) {
                sender.sendMessage("§cУкажите имя мира или all.");
                return true;
            }

            List<CleanThread> threads = new ArrayList<>();
            String worldName = args[1];
            if (args[1].equalsIgnoreCase("all")) {
                threads.addAll(cleanMap.values());
            } else {
                CleanThread thread = cleanMap.get(new File(Bukkit.getWorldContainer(), worldName));
                if (thread == null) {
                    sender.sendMessage("§cПроцесс очистки мира " + worldName + " не найден, список процессов: " +
                            cleanMap.values().stream().map(cleanThread -> cleanThread.getWorld().getName())
                                    .collect(Collectors.joining(", ")));
                    return true;
                }
                threads.add(thread);
            }

            for (CleanThread thread : threads) {
                thread.interrupt();
                sender.sendMessage("§aОстанавливаем процесс очистки мира " + thread.getWorld().getName() + "...");
            }
            return true;
        }

        sender.sendMessage("§cАргумент команды не найден, подробнее - /worldcleaner help");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return filterTabResponse(Arrays.asList("reload", "startclean", "stopclean", "listclean"), args);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("startclean")) {
            List<String> suspend = new ArrayList<>();
            List<File> allWorlds = new ArrayList<>();
            for (File world : Bukkit.getWorldContainer().listFiles(File::isDirectory)) {
                File regionFir = new File(world, "region");
                if (regionFir.exists() && regionFir.isDirectory()) {
                    allWorlds.add(world);
                }
            }
            for (File world : allWorlds) {
                suspend.add(world.getName());
            }
            suspend.add("all");
            return filterTabResponse(suspend, args);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("stopclean")) {
            ArrayList<String> suspend = new ArrayList<>();
            for (File world : cleanMap.keySet()) {
                suspend.add(world.getName());
            }
            suspend.add("all");
            return filterTabResponse(suspend, args);
        }

        return Collections.emptyList();
    }

    public static List<String> filterTabResponse(List<String> list, String[] args) {
        return list.stream()
                .filter(el -> StringUtils.containsIgnoreCase(el, args[args.length - 1]))
                .collect(Collectors.toList());
    }

    public long getInhabitedTimeThresholdTicks() {
        return inhabitedTimeThresholdTicks;
    }

    public void onDone(CleanThread cleanThread) {
        this.cleanMap.remove(cleanThread.getWorld());
    }
}
