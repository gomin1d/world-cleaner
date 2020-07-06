package ua.lokha.megachunkfixer2000;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Data
@AllArgsConstructor
public class ParseArgs {

    private static final FilenameFilter filter = (dir, name) -> name.endsWith(".mca");

    private File dir;
    private List<File> regions;
    private List<String> flags;

    @SuppressWarnings("ConstantConditions")
    public static ParseArgs parse(String[] args) {
        List<String> flags = Stream.of(args).filter(s -> s.startsWith("--")).collect(Collectors.toList());

        File world;
        if (args.length > 0) {
            world = new File(String.join(" ", Stream.of(args).filter(s -> !s.startsWith("--")).collect(Collectors.joining(" "))));
            if (!world.exists()) {
                throw new RuntimeException("Папка " + world + " не найдена.");
            } else if (!world.isDirectory()) {
                throw new RuntimeException("Это не папка - " + world + ".");
            }
        } else {
            world = new File(".");
        }

        List<File> files = new ArrayList<>(Arrays.asList(world.listFiles(filter)));
        File regionDir = new File(world, "region");
        if (!regionDir.exists()) {
            File[] dims = world.listFiles(pathname -> pathname.isDirectory() && pathname.getName().startsWith("DIM"));
            if (dims.length > 0) {
                regionDir = new File(dims[0], "region");
            }
        }
        if (regionDir.exists() && regionDir.isDirectory()) {
            files.addAll(Arrays.asList(regionDir.listFiles(filter)));
        }

        return new ParseArgs(world, files, flags);
    }

    public boolean hasFlag(String flag) {
        return flags.contains(flag);
    }
}
