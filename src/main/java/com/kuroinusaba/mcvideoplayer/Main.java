import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.map.MinecraftFont;
import org.bukkit.map.MinecraftFontWrapper;
import org.bukkit.map.MinecraftFontWrapper.FontDirection;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;

public class VideoPlugin extends JavaPlugin {
    private Map<String, MapView> videoScreens = new HashMap<>();
    private Map<String, FileConfiguration> screenConfigs = new HashMap<>();

    @Override
    public void onEnable() {
        // プラグインが有効になった時の処理
    }

    @Override
    public void onDisable() {
        // プラグインが無効になった時の処理
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("video")) {
            if (args.length < 2) {
                sender.sendMessage("Usage: /video create <screenName>");
                sender.sendMessage("Usage: /video play <screenName> <videoURL>");
                sender.sendMessage("Usage: /video reload <screenName>");
                return true;
            }

            String action = args[0];
            String screenName = args[1];

            if (action.equalsIgnoreCase("create")) {
                createVideoScreen((Player) sender, screenName);
                sender.sendMessage("Video screen created!");
            } else if (action.equalsIgnoreCase("play")) {
                if (args.length < 3) {
                    sender.sendMessage("Usage: /video play <screenName> <videoURL>");
                    return true;
                }
                String videoURL = args[2];
                playVideo((Player) sender, screenName, videoURL);
                sender.sendMessage("Video started!");
            } else if (action.equalsIgnoreCase("reload")) {
                reloadScreen((Player) sender, screenName);
                sender.sendMessage("Video screen reloaded!");
            }

            return true;
        }

        return false;
    }

    private void createVideoScreen(Player player, String screenName) {
        Location frameLocation = player.getLocation().add(0, 1, 0); // プレイヤーの上にスクリーンを作成
        ItemFrame itemFrame = frameLocation.getWorld().spawn(frameLocation, ItemFrame.class);
        itemFrame.setFacingDirection(player.getFacing().getOppositeFace());
        itemFrame.setRotation(BlockFace.SOUTH);

        MapView mapView = getServer().createMap(frameLocation.getWorld());
        mapView.setScale(MapView.Scale.NORMAL);
        mapView.addRenderer(new VideoMapRenderer(screenName));

        itemFrame.setMapView(mapView);

        videoScreens.put(screenName, mapView);
        createScreenConfig(screenName);
    }

    private void playVideo(Player player, String screenName, String videoURL) {
        MapView mapView = videoScreens.get(screenName);
        if (mapView == null) {
            player.sendMessage("Video screen not found!");
            return;
        }

        try {
            BufferedImage image = ImageIO.read(new URL(videoURL));
            if (image == null) {
                player.sendMessage("Failed to load video!");
                return;
            }

            MapRenderer mapRenderer = mapView.getRenderers().get(0);
            if (!(mapRenderer instanceof VideoMapRenderer)) {
                player.sendMessage("Invalid video screen!");
                return;
            }

            VideoMapRenderer videoMapRenderer = (VideoMapRenderer) mapRenderer;
            videoMapRenderer.playVideo(image);

            player.sendMessage("Video started!");
        } catch (IOException e) {
            player.sendMessage("Error loading video!");
        }
    }

    private void reloadScreen(Player player, String screenName) {
        MapView mapView = videoScreens.get(screenName);
        if (mapView == null) {
            player.sendMessage("Video screen not found!");
            return;
        }

        FileConfiguration config = screenConfigs.get(screenName);
        if (config == null) {
            player.sendMessage("Screen configuration not found!");
            return;
        }

        int height = config.getInt("height");
        int width = config.getInt("width");
        String playtime = config.getString("playtime");

        VideoMapRenderer mapRenderer = new VideoMapRenderer(screenName);
        mapView.getRenderers().forEach(mapView::removeRenderer);
        mapView.addRenderer(mapRenderer);

        mapRenderer.setHeight(height);
        mapRenderer.setWidth(width);
        mapRenderer.setPlaytime(playtime);

        player.sendMessage("Video screen reloaded!");
    }

    private void createScreenConfig(String screenName) {
        File configFile = new File(getDataFolder(), screenName + ".yml");
        if (!configFile.exists()) {
            saveResource(screenName + ".yml", false);
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        screenConfigs.put(screenName, config);
    }

    private class VideoMapRenderer extends MapRenderer {
        private final String screenName;
        private int height;
        private int width;
        private BufferedImage videoFrame;
        private String playtime;

        public VideoMapRenderer(String screenName) {
            this.screenName = screenName;
        }

        public void setHeight(int height) {
            this.height = height;
        }

        public void setWidth(int width) {
            this.width = width;
        }

        public void setPlaytime(String playtime) {
            this.playtime = playtime;
        }

        public void playVideo(BufferedImage videoImage) {
            this.videoFrame = videoImage;

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (videoFrame != null) {
                        updateMap();
                    } else {
                        cancel();
                    }
                }
            }.runTaskTimer(VideoPlugin.this, 0, 1); // 1ティックごとに画像を更新
        }

        private void updateMap() {
            LocalTime currentTime = LocalTime.now();
            LocalTime targetTime = LocalTime.parse(playtime, DateTimeFormatter.ofPattern("HH:mm"));

            if (currentTime.isAfter(targetTime)) {
                for (MapRenderer renderer : getMapView().getRenderers()) {
                    getMapView().removeRenderer(renderer);
                }

                BufferedImage frame = videoFrame.getSubimage(0, 0, width, height);

                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int color = frame.getRGB(x, y);
                        getMapView().setPixel(x, y, (byte) ((color >> 16) & 0xFF), (byte) ((color >> 8) & 0xFF), (byte) (color & 0xFF));
                    }
                }

                getMapView().addRenderer(this);
            }
        }

        @Override
        public void render(MapView mapView, MapCanvas mapCanvas, Player player) {
            MinecraftFontWrapper font = new MinecraftFontWrapper(MinecraftFont.Font, 8, FontDirection.LEFT_TO_RIGHT);
            String text = "Playing video...";
            int textWidth = font.getWidth(text);

            int x = (mapCanvas.getCursors().length - textWidth) / 2;
            int y = (mapCanvas.getCursors()[0].length - 8) / 2;
            font.drawText(mapCanvas, x, y, text);
        }
    }
}
