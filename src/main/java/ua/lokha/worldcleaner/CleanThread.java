package ua.lokha.worldcleaner;

import ua.lokha.megachunkfixer2000.NBTStreamReader;
import ua.lokha.megachunkfixer2000.RegionFile;
import ua.lokha.megachunkfixer2000.Utils;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Map;

public class CleanThread extends Thread {

    private File world;
    private Main main;
    private int deletedTotal = 0;
    private int checkTotal = 0;
    private long beforeCleanUsedTotal = 0;
    private long afterCleanUsedTotal = 0;

    public CleanThread(File world, Main main) {
        super("Clean World " + world.getName());
        this.world = world;
        this.main = main;
    }

    @Override
    public void run() {
        main.getLogger().info("Запускаем процесс очистки мира " + world.getName());

        try {
            this.clean();
        } finally {
            main.onDone(this);
        }
    }

    @SuppressWarnings("ConstantConditions")
    private void clean() {
        for (File file : new File(world, "region").listFiles((dir, name) -> name.endsWith(".mca"))) {
            if (this.isInterrupted()) {
                return;
            }
            main.getLogger().info("Очищаем " + file);
            int regionX;
            int regionZ;
            try {
                String[] data = file.getName().split("\\.");
                regionX = Integer.parseInt(data[1]);
                regionZ = Integer.parseInt(data[2]);
            } catch (NumberFormatException e) {
                main.getLogger().severe("В файле " + file + " не правильный формат имени");
                e.printStackTrace();
                continue;
            }

            long beforeLength = file.length();
            beforeCleanUsedTotal += beforeLength;
            boolean clean = false;
            int chunkCount = 0;
            try (RegionFile regionFile = new RegionFile(file)) {
                for (int chunkOffX = 0; chunkOffX < 32; chunkOffX++) {
                    for (int chunkOffZ = 0; chunkOffZ < 32; chunkOffZ++) {
                        if (this.isInterrupted()) {
                            return;
                        }
                        if (regionFile.hasChunk(chunkOffX, chunkOffZ)) {
                            checkTotal++;
                            try (DataInputStream inputStream = regionFile.getChunkDataInputStream(chunkOffX, chunkOffZ)) {
                                Map<String, Object> read = NBTStreamReader.read(inputStream, false);
                                Map level = (Map) read.get("Level");
                                if (level == null) {
                                    main.getLogger().severe("В чанке " + file + " x=" + chunkOffX + " z=" + chunkOffZ + " нет тега Level");
                                    continue;
                                }
                                Number ticks = (Number) level.get("InhabitedTime");
                                if (ticks == null) {
                                    main.getLogger().severe("В чанке " + file + " x=" + chunkOffX + " z=" + chunkOffZ + " нет тега InhabitedTime");
                                    continue;
                                }
                                int chunkX = (regionX << 5) + chunkOffX;
                                int chunkZ = (regionZ << 5) + chunkOffZ;

                                if (ticks.longValue() < main.getInhabitedTimeThresholdTicks()) {
                                    main.getLogger().info("Удаляем чанк " + file.getName() + " " +
                                            "offX=" + chunkOffX + " offZ=" + chunkOffZ + ", " +
                                            "x=" + chunkX + " z=" + chunkZ + ", " +
                                            "в нем InhabitedTime " + ticks.longValue() + " меньше порога " + main.getInhabitedTimeThresholdTicks());
                                    regionFile.deleteChunk(chunkOffX, chunkOffZ);
                                    deletedTotal++;
                                    clean = true;
                                } else {
                                    chunkCount++;
                                }
                            } catch (IOException e) {
                                main.getLogger().severe("Ошибка чтения чанка " + file + " x=" + chunkOffX + " z=" + chunkOffZ);
                                e.printStackTrace();
                            }
                        }
                    }
                } // end for

                if (clean) {
                    regionFile.clearUnusedSpace();
                }
            }

            long afterLength = file.length();
            afterCleanUsedTotal += afterLength;

            if (chunkCount == 0) {
                main.getLogger().info("Регион " + file.getName() + " не содержит ни одного чанка, удаляем его...");
                file.delete();
            }
        }

        main.getLogger().info("Закончили процесс очистки мира " + world.getName() +
                ", удалили " + deletedTotal + " чанков" +
                ", сократили размер мира " +
                "с " + Utils.toLogLength(beforeCleanUsedTotal) + " " +
                "до " + Utils.toLogLength(afterCleanUsedTotal) + " " +
                "(-" + Utils.toLogPercent(afterCleanUsedTotal, beforeCleanUsedTotal) + "%).");
    }

    public File getWorld() {
        return world;
    }

    public long getAfterCleanUsedTotal() {
        return afterCleanUsedTotal;
    }

    public long getBeforeCleanUsedTotal() {
        return beforeCleanUsedTotal;
    }

    public int getDeletedTotal() {
        return deletedTotal;
    }

    public int getCheckTotal() {
        return checkTotal;
    }
}

 /* Дебаг визуализация
            int size = chunks.stream()
                    .mapToInt(value -> Math.max(Math.abs(value.getChunkX()), Math.abs(value.getChunkZ())))
                    .max().orElse(0);

            long maxTime = chunks.stream()
                    .mapToLong(ChunkInfo::getTime)
                    .max().orElse(0);

            size += 10;
            size *= 2;

            BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
            for(int x = 0; x < size; x++){
                for(int y = 0; y < size; y++){
                    image.setRGB(x, y, Color.WHITE.getRGB());
                }
            }

            for(ChunkInfo chunk : chunks){
                int x = (size / 2) + chunk.getChunkX();
                int y = (size / 2) + chunk.getChunkZ();
                int gradient = 255 - (int) (((double) chunk.getTime() / maxTime) * 255);
                image.setRGB(x, y, new Color(255, gradient, gradient).getRGB());
            }

            try {
                ImageIO.write(image, "png", new File(worldName + "-out.png"));
            } catch (IOException e) {
                e.printStackTrace();
            }*/