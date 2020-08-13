package com.gamingmesh.jobs.Signs;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.Skull;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import com.gamingmesh.jobs.Jobs;
import com.gamingmesh.jobs.CMILib.CMIMaterial;
import com.gamingmesh.jobs.CMILib.ConfigReader;
import com.gamingmesh.jobs.CMILib.Version;
import com.gamingmesh.jobs.container.Job;
import com.gamingmesh.jobs.container.TopList;

public class SignUtil {

    private HashMap<String, HashMap<String, jobsSign>> SignsByType = new HashMap<>();
    private HashMap<String, jobsSign> SignsByLocation = new HashMap<>();

    public HashMap<String, HashMap<String, jobsSign>> getSigns() {
	return SignsByType;
    }

    public boolean removeSign(Location loc) {
	jobsSign jSign = SignsByLocation.remove(jobsSign.locToBlockString(loc));
	if (jSign == null)
	    return false;

	HashMap<String, jobsSign> sub = SignsByType.get(jSign.getIdentifier().toLowerCase());
	if (sub != null) {
	    sub.remove(jSign.locToBlockString());
	}
	return true;
    }

    public jobsSign getSign(Location loc) {
	if (loc == null)
	    return null;

	return SignsByLocation.get(jobsSign.locToBlockString(loc));
    }

    public void addSign(jobsSign jSign) {
	if (jSign == null)
	    return;

	SignsByLocation.put(jSign.locToBlockString(), jSign);

	HashMap<String, jobsSign> old = SignsByType.get(jSign.getIdentifier().toLowerCase());
	if (old == null) {
	    old = new HashMap<String, jobsSign>();
	    SignsByType.put(jSign.getIdentifier().toLowerCase(), old);
	}

	old.put(jSign.locToBlockString(), jSign);

	SignsByType.put(jSign.getIdentifier().toLowerCase(), old);
    }

    public void LoadSigns() {
	if (!Jobs.getGCManager().SignsEnabled)
	    return;

	SignsByType.clear();
	SignsByLocation.clear();

	File file = new File(Jobs.getFolder(), "Signs.yml");
	YamlConfiguration f = YamlConfiguration.loadConfiguration(file);

	if (!f.isConfigurationSection("Signs"))
	    return;

	ConfigurationSection ConfCategory = f.getConfigurationSection("Signs");
	ArrayList<String> categoriesList = new ArrayList<>(ConfCategory.getKeys(false));
	if (categoriesList.isEmpty())
	    return;

	for (String category : categoriesList) {
	    ConfigurationSection NameSection = ConfCategory.getConfigurationSection(category);
	    jobsSign newTemp = new jobsSign();
	    if (NameSection.isString("World")) {
		newTemp.setWorldName(NameSection.getString("World"));
		newTemp.setX((int) NameSection.getDouble("X"));
		newTemp.setY((int) NameSection.getDouble("Y"));
		newTemp.setZ((int) NameSection.getDouble("Z"));
	    } else {
		newTemp.setLoc(NameSection.getString("Loc"));
	    }
	    if (NameSection.isString("Type"))
		newTemp.setType(SignTopType.getType(NameSection.getString("Type")));

	    newTemp.setNumber(NameSection.getInt("Number"));
	    if (NameSection.isString("JobName")) {
		SignTopType t = SignTopType.getType(NameSection.getString("JobName"));
		if (t == null)
		    newTemp.setJobName(NameSection.getString("JobName"));
	    }
	    newTemp.setSpecial(NameSection.getBoolean("Special"));

	    HashMap<String, jobsSign> old = SignsByType.get(newTemp.getIdentifier().toLowerCase());
	    if (old == null) {
		old = new HashMap<String, jobsSign>();
		SignsByType.put(newTemp.getIdentifier().toLowerCase(), old);
	    }
	    String loc = newTemp.locToBlockString();
	    old.put(loc, newTemp);
	    SignsByLocation.put(loc, newTemp);
	}

	if (!SignsByLocation.isEmpty()) {
	    Jobs.consoleMsg("&e[Jobs] Loaded " + SignsByLocation.size() + " top list signs");
	}
    }

    public void saveSigns() {
	File f = new File(Jobs.getFolder(), "Signs.yml");
	YamlConfiguration conf = YamlConfiguration.loadConfiguration(f);

	ConfigReader reader = null;
	try {
	    reader = new ConfigReader(f);
	} catch (Exception e1) {
	    e1.printStackTrace();
	    return;
	}

	conf.options().copyDefaults(true);
	reader.addComment("Signs", "DO NOT EDIT THIS FILE BY HAND!");

	if (!conf.isConfigurationSection("Signs"))
	    conf.createSection("Signs");

	int i = 0;
	for (Entry<String, jobsSign> one : SignsByLocation.entrySet()) {
	    jobsSign sign = one.getValue();
	    ++i;
	    String path = "Signs." + i;
	    reader.set(path + ".Loc", sign.locToBlockString());
	    reader.set(path + ".Number", sign.getNumber());
	    reader.set(path + ".Type", sign.getType().toString());
	    reader.set(path + ".JobName", sign.getJobName());
	    reader.set(path + ".Special", sign.isSpecial());
	}

	try {
	    reader.save(f);
	} catch (IOException e) {
	    e.printStackTrace();
	}
    }

    public void updateAllSign(Job job) {
	for (SignTopType types : SignTopType.values()) {
	    if (types == SignTopType.questtoplist)
		continue;
	    SignUpdate(job, types);
	}
    }

    public boolean SignUpdate(Job job) {
	return SignUpdate(job, SignTopType.toplist);
    }

    public boolean SignUpdate(Job job, SignTopType type) {

	if (!Jobs.getGCManager().SignsEnabled)
	    return true;

	if (type == null)
	    type = SignTopType.toplist;

	String JobNameOrType = jobsSign.getIdentifier(job, type);

	HashMap<String, jobsSign> signs = this.SignsByType.get(JobNameOrType.toLowerCase());
	if (signs == null || signs.isEmpty())
	    return false;

	List<TopList> PlayerList = new ArrayList<>();

	switch (type) {
	case gtoplist:
	    PlayerList = Jobs.getJobsDAO().getGlobalTopList(0);
	    break;
	case questtoplist:
	    PlayerList = Jobs.getJobsDAO().getQuestTopList(0);
	    break;
	default:
	    break;
	}

	int timelapse = 1;

	HashMap<String, List<TopList>> temp = new HashMap<>();

	boolean save = false;
	for (Entry<String, jobsSign> one : (new HashMap<String, jobsSign>(signs)).entrySet()) {
	    jobsSign jSign = one.getValue();
	    String SignJobName = jSign.getJobName();
	    Location loc = jSign.getLocation();
	    if (loc == null)
		continue;

	    Block block = loc.getBlock();
	    if (!(block.getState() instanceof Sign)) {
		if (!JobNameOrType.isEmpty()) {
		    HashMap<String, jobsSign> tt = this.SignsByType.get(JobNameOrType.toLowerCase());
		    if (tt != null) {
			tt.remove(jSign.locToBlockString());
		    }
		}

		this.SignsByLocation.remove(jSign.locToBlockString());
		save = true;
		continue;
	    }

	    if (type == SignTopType.toplist) {
		PlayerList = temp.get(SignJobName);
		if (PlayerList == null) {
		    PlayerList = Jobs.getJobsDAO().toplist(SignJobName);
		    temp.put(SignJobName, PlayerList);
		}
	    }

	    if (PlayerList.isEmpty())
		continue;

	    int number = jSign.getNumber() - 1;
	    Sign sign = (Sign) block.getState();

	    if (!jSign.isSpecial()) {
		for (int i = 0; i < 4; i++) {
		    if (i + number >= PlayerList.size()) {
			sign.setLine(i, "");
			continue;
		    }

		    TopList pl = PlayerList.get(i + number);
		    String PlayerName = pl.getPlayerInfo().getName();
		    if (PlayerName.length() > 15) {
			// We need to split 10 char of name, because of sign rows
			PlayerName = PlayerName.split("(?<=\\G.{10})")[0] + "~";
		    }

		    String line = "";
		    switch (type) {
		    case toplist:
		    case gtoplist:
			line = Jobs.getLanguage().getMessage("signs.List", "[number]", i + number + 1, "[player]", PlayerName, "[level]", pl.getLevel());
			break;
		    case questtoplist:
			line = Jobs.getLanguage().getMessage("signs.questList", "[number]", i + number + 1, "[player]", PlayerName, "[quests]", pl.getLevel());
			break;
		    default:
			break;
		    }

		    if (!line.isEmpty())
			sign.setLine(i, line);
		}
		sign.update();
		if (!UpdateHead(sign, PlayerList.get(0).getPlayerInfo().getName(), timelapse)) {
		    timelapse--;
		}
	    } else {
		if (jSign.getNumber() > PlayerList.size())
		    continue;

		TopList pl = PlayerList.get(jSign.getNumber() - 1);
		String PlayerName = pl.getPlayerInfo().getName();
		if (PlayerName.length() > 15) {
		    PlayerName = PlayerName.split("(?<=\\G.{10})")[0] + "~";
		}

		int no = jSign.getNumber() + number + 1;
		sign.setLine(0, translateSignLine("signs.SpecialList.p" + jSign.getNumber(), no, PlayerName, pl.getLevel(), SignJobName));
		sign.setLine(1, translateSignLine("signs.SpecialList.name", no, PlayerName, pl.getLevel(), SignJobName));

		switch (type) {
		case toplist:
		case gtoplist:
		    sign.setLine(2, Jobs.getLanguage().getMessage("signs.SpecialList.level", "[number]", no, "[player]", PlayerName, "[level]", pl.getLevel(), "[job]", SignJobName));
		    break;
		case questtoplist:
		    sign.setLine(2, Jobs.getLanguage().getMessage("signs.SpecialList.quests", "[number]", no, "[player]", PlayerName, "[quests]", pl.getLevel(), "[job]", SignJobName));
		    break;
		default:
		    break;
		}

		sign.setLine(3, translateSignLine("signs.SpecialList.bottom", no, PlayerName, pl.getLevel(), SignJobName));
		sign.update();
		if (!UpdateHead(sign, pl.getPlayerInfo().getName(), timelapse)) {
		    timelapse--;
		}
	    }
	    timelapse++;
	}

	if (save)
	    saveSigns();

	return true;
    }

    private static String translateSignLine(String path, int number, String playerName, int level, String jobname) {
	return Jobs.getLanguage().getMessage(path,
	    "[number]", number,
	    "[player]", playerName,
	    "[level]", level,
	    "[job]", jobname);
    }

    @SuppressWarnings("deprecation")
    public boolean UpdateHead(final Sign sign, final String Playername, int timelapse) {
	if (Playername == null)
	    return false;

	timelapse = timelapse < 1 ? 1 : timelapse;

	BlockFace directionFacing = null;
	if (Version.isCurrentEqualOrLower(Version.v1_13_R2)) {
	    org.bukkit.material.Sign signMat = (org.bukkit.material.Sign) sign.getData();
	    directionFacing = signMat.getFacing();
	} else {
	    if (CMIMaterial.isWallSign(sign.getType())) {
		org.bukkit.block.data.type.WallSign data = (org.bukkit.block.data.type.WallSign) sign.getBlockData();
		directionFacing = data.getFacing();
	    } else {
		org.bukkit.block.data.type.Sign data = (org.bukkit.block.data.type.Sign) sign.getBlockData();
		directionFacing = data.getRotation();
	    }
	}

	final Location loc = sign.getLocation().clone();
	loc.add(0, 1, 0);

	Block block = loc.getBlock();
	if (directionFacing != null && (block == null || !(block.getState() instanceof Skull)))
	    loc.add(directionFacing.getOppositeFace().getModX(), 0, directionFacing.getOppositeFace().getModZ());

	Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(Jobs.getInstance(), new Runnable() {
	    @Override
	    public void run() {
		Block b = loc.getBlock();
		if (b == null || !(b.getState() instanceof Skull))
		    return;

		Skull skull = (Skull) b.getState();
		if (skull == null)
		    return;

		if (skull.getOwner() != null && skull.getOwner().equalsIgnoreCase(Playername))
		    return;

		skull.setOwner(Playername);
		skull.update();
	    }
	}, timelapse * Jobs.getGCManager().InfoUpdateInterval * 20L);
	return true;
    }
}
