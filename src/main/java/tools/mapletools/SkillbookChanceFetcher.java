package tools.mapletools;

import server.life.MonsterStats;
import tools.Pair;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author RonanLana
 * <p>
 * This application traces missing meso drop data on the underlying DB (that must be
 * defined on the DatabaseConnection file of this project) and generates a
 * SQL file that proposes missing drop entries for the drop_data table.
 * <p>
 * The meso range is calculated accordingly with the target mob stats, such as level
 * and if it's a boss or not, similarly as how it has been done for the actual meso
 * drops.
 */
public class SkillbookChanceFetcher {
    private static final File OUTPUT_FILE = ToolConstants.getOutputFile("skillbook_drop_data.sql");
    private static final Map<Pair<Integer, Integer>, Integer> skillbookChances = new HashMap<>();

    private static PrintWriter printWriter;
    private static Map<Integer, MonsterStats> mobStats;

    private static List<Map.Entry<Pair<Integer, Integer>, Integer>> sortedSkillbookChances() {
        List<Map.Entry<Pair<Integer, Integer>, Integer>> skillbookChancesList = new ArrayList<>(skillbookChances.entrySet());

        skillbookChancesList.sort((o1, o2) -> {
            if (o1.getKey().getLeft().equals(o2.getKey().getLeft())) {
                return o1.getKey().getRight() < o2.getKey().getRight() ? -1 : (o1.getKey().getRight().equals(o2.getKey().getRight()) ? 0 : 1);
            }

            return (o1.getKey().getLeft() < o2.getKey().getLeft()) ? -1 : 1;
        });

        return skillbookChancesList;
    }

    private static boolean isLegendSkillUpgradeBook(int itemid) {
        int itemidBranch = itemid / 10000;
        return (itemidBranch == 228 && itemid >= 2280013 || itemidBranch == 229 && itemid >= 2290126);      // drop rate of Legends are higher
    }

    private static void fetchSkillbookDropChances() {
        Connection con = SimpleDatabaseConnection.getConnection();

        try {
            PreparedStatement ps = con.prepareStatement("SELECT dropperid, itemid FROM drop_data WHERE itemid >= 2280000 AND itemid < 2300000");
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                int mobid = rs.getInt("dropperid");
                int itemid = rs.getInt("itemid");

                int expectedChance = 250;

                if (mobStats.get(mobid) != null) {
                    int level = mobStats.get(mobid).getLevel();
                    expectedChance *= Math.max(2, (level - 80) / 15);

                    if (mobStats.get(mobid).isBoss()) {
                        expectedChance *= 20;
                    } else {
                        expectedChance *= 1;
                    }
                } else {
                    expectedChance = 1287;
                }

                if (isLegendSkillUpgradeBook(itemid)) {     // drop rate of Legends seems to be higher than explorers, in retrospect from values in DB
                    expectedChance *= 3;
                }

                skillbookChances.put(new Pair<>(mobid, itemid), expectedChance);
            }

            rs.close();
            ps.close();
            con.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void printSkillbookChanceUpdateSqlHeader() {
        printWriter.println(" # SQL File autogenerated from the MapleSkillbookChanceFetcher feature by Ronan Lana.");
        printWriter.println(" # Generated data takes into account mob stats such as level and boss for the chance rates.");
        printWriter.println();

        printWriter.println("  REPLACE INTO drop_data (`dropperid`, `itemid`, `minimum_quantity`, `maximum_quantity`, `questid`, `chance`) VALUES");
    }

    private static void generateSkillbookChanceUpdateFile() {
        try {
            printWriter = new PrintWriter(OUTPUT_FILE, StandardCharsets.UTF_8);

            printSkillbookChanceUpdateSqlHeader();

            List<Map.Entry<Pair<Integer, Integer>, Integer>> skillbookChancesList = sortedSkillbookChances();
            for (Map.Entry<Pair<Integer, Integer>, Integer> e : skillbookChancesList) {
                printWriter.println("(" + e.getKey().getLeft() + ", " + e.getKey().getRight() + ", 1, 1, 0, " + e.getValue() + "),");
            }

            printWriter.close();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    public static void main(String[] args) {
        // load mob stats from WZ
        mobStats = MonsterStatFetcher.getAllMonsterStats();

        fetchSkillbookDropChances();
        generateSkillbookChanceUpdateFile();
    }
}
