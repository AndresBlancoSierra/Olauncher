package app.olauncher.data

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import org.json.JSONObject
import java.time.LocalDate

data class GymExercise(
    val name: String,
    val initial: Float?,
    val last: Float?,
    val delta: Float?,
    val sessions: Int,
) {
    val hasKilos: Boolean get() = initial != null && last != null
}

data class SpecialStat(
    val letter: String,
    val name: String,
    val max: Int?,
    val desc: String,
)

data class VaultSnapshot(
    val gym: Map<String, List<GymExercise>>,
    val streaks: Map<String, Int>,
    val lastStreak: Map<String, String>,
    val done: Map<String, String>,
    val readDone: List<String>,
    val readProgress: List<String>,
) {
    companion object {
        val EMPTY = VaultSnapshot(emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyList(), emptyList())
    }
}

/**
 * Lectura SOLO-LECTURA del vault de Obsidian (por fotograma del wallpaper de PC).
 * Port de server/serve.py -> gym/points/read.
 */
object VaultRepository {

    const val POINTS_FILE = "points.md"
    const val READ_DIR = "Read"
    const val READ_FILE = "Read.md"
    const val GYM_DIR = "GYM"
    val GYM_GROUPS = listOf("Push", "Pull", "Leg")

    fun hasVault(prefs: Prefs): Boolean = !prefs.vaultTreeUri.isNullOrEmpty()

    fun loadVault(context: Context, treeUri: String): VaultSnapshot? {
        val uri = try {
            android.net.Uri.parse(treeUri)
        } catch (e: Exception) {
            return null
        }
        val root = DocumentFile.fromTreeUri(context, uri) ?: return null
        val gym = readGym(context, root)
        val (streaks, last, done) = readPoints(context, root)
        val (readDone, readProgress) = readRead(context, root)
        return VaultSnapshot(gym, streaks, last, done, readDone, readProgress)
    }

    private fun readGym(context: Context, root: DocumentFile): Map<String, List<GymExercise>> {
        val result = linkedMapOf<String, List<GymExercise>>()
        val gymDir = root.findFile(GYM_DIR)
        for (group in GYM_GROUPS) {
            val folder = gymDir?.findFile(group) ?: continue
            val list = mutableListOf<GymExercise>()
            val files = folder.listFiles().filter { it.name?.endsWith(".md") == true }.sortedBy { it.name }
            for (file in files) {
                val text = readText(context, file.uri) ?: continue
                list.add(parseGymMarkdown(file.name?.removeSuffix(".md") ?: "", text))
            }
            result[group] = list
        }
        return result
    }

    fun parseGymMarkdown(name: String, text: String): GymExercise {
        val rows = text.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("|") }
            .map { line -> line.trim('|').split("|").map { c -> c.trim() } }
            .toList()
        val sessions = mutableListOf<Float>()
        for (r in rows.drop(2)) {
            val w = r.firstOrNull()?.replace(",", ".")?.toFloatOrNull()
            if (w != null) sessions.add(w)
        }
        return if (sessions.isNotEmpty()) {
            GymExercise(
                name = name,
                initial = sessions.first(),
                last = sessions.last(),
                delta = sessions.last() - sessions.first(),
                sessions = sessions.size,
            )
        } else {
            GymExercise(name, null, null, null, 0)
        }
    }

    private fun readPoints(context: Context, root: DocumentFile): Triple<Map<String, Int>, Map<String, String>, Map<String, String>> {
        val file = root.findFile(POINTS_FILE) ?: return Triple(emptyMap(), emptyMap(), emptyMap())
        val text = readText(context, file.uri) ?: return Triple(emptyMap(), emptyMap(), emptyMap())
        return parsePoints(text)
    }

    fun parsePoints(text: String): Triple<Map<String, Int>, Map<String, String>, Map<String, String>> {
        val block = StringBuilder()
        var inBlock = false
        for (line in text.lines()) {
            val s = line.trim()
            if (s.startsWith("```json")) { inBlock = true; continue }
            if (inBlock && s.startsWith("```")) { inBlock = false; continue }
            if (inBlock) block.appendLine(line)
        }
        if (block.isEmpty()) return Triple(emptyMap(), emptyMap(), emptyMap())
        val json = try {
            JSONObject(block.toString())
        } catch (e: Exception) {
            return Triple(emptyMap(), emptyMap(), emptyMap())
        }
        val streaks = mutableMapOf<String, Int>()
        val last = mutableMapOf<String, String>()
        val streaksJson = json.optJSONObject("streaks")
        if (streaksJson != null) {
            val keys = streaksJson.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = streaksJson.optJSONObject(k)
                if (v != null) {
                    streaks[k] = v.optInt("n", 0)
                    last[k] = v.optString("last", "")
                }
            }
        }
        val done = mutableMapOf<String, String>()
        val doneJson = json.optJSONObject("done")
        if (doneJson != null) {
            val keys = doneJson.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                done[k] = doneJson.optString(k, "")
            }
        }
        return Triple(streaks, last, done)
    }

    private fun readRead(context: Context, root: DocumentFile): Pair<List<String>, List<String>> {
        val dir = root.findFile(READ_DIR) ?: return Pair(emptyList(), emptyList())
        val file = dir.findFile(READ_FILE) ?: return Pair(emptyList(), emptyList())
        val text = readText(context, file.uri) ?: return Pair(emptyList(), emptyList())
        return parseRead(text)
    }

    fun parseRead(text: String): Pair<List<String>, List<String>> {
        val done = mutableListOf<String>()
        val progress = mutableListOf<String>()
        val re = Regex("""^\s*-\s*\[([ xX])\]\s*(.+?)\s*$""")
        for (line in text.lines()) {
            val m = re.matchEntire(line)
            if (m != null) {
                val title = m.groupValues[2].trim()
                if (m.groupValues[1].lowercase() == "x") done.add(title) else progress.add(title)
            }
        }
        return Pair(done, progress)
    }

    private fun readText(context: Context, uri: android.net.Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        } catch (e: Exception) {
            null
        }
    }
}

/** Lógica de visualización del Pip-Boy (port de fallout-stats.html). */
object VaultLogic {

    val STATS = listOf(
        SpecialStat("G", "GYM", 365, "RUTINA DIARIA Y PROGRESO EN KG (PUSH/PULL/LEG)"),
        SpecialStat("V", "VOLLEY", 365, "RENDIMIENTO EN LA CANCHA"),
        SpecialStat("M", "MEDITATION", 365, "PRÁCTICA DIARIA DE MEDITACIÓN"),
        SpecialStat("D", "DRAW", 365, "DIBUJO DIARIO"),
        SpecialStat("C", "COOL SHOWER", 365, "DUCHA FRÍA DIARIA"),
        SpecialStat("R", "READ", 365, "LECTURA DIARIA — LIBROS TERMINADOS Y EN PROGRESO"),
    )
    val STAT_BY_NAME: Map<String, SpecialStat> = STATS.associateBy { it.name }

    private val GYM_DAYS = mapOf(
        1 to "PUSH", 4 to "PUSH",
        2 to "PULL", 5 to "PULL",
        3 to "LEG", 6 to "LEG",
    )
    private val GROUP_FOLDER = mapOf(
        "Push" to "PUSH", "Pull" to "PULL", "Leg" to "LEG",
    )
    private val DAY_NAMES = listOf("DOMINGO", "LUNES", "MARTES", "MIÉRCOLES", "JUEVES", "VIERNES", "SÁBADO")

    fun todayStr(): String = LocalDate.now().toString()
    fun yesterdayStr(): String = LocalDate.now().minusDays(1).toString()
    fun todayGymStat(): String? = GYM_DAYS[LocalDate.now().dayOfWeek.value]
    fun todayGymFolder(): String? = GROUP_FOLDER.entries.firstOrNull { it.value == todayGymStat() }?.key
    fun isGymDay(statName: String): Boolean {
        if (statName == "GYM") return todayGymStat() != null
        return GYM_DAYS[LocalDate.now().dayOfWeek.value] == statName
    }
    fun isEligible(statName: String): Boolean = if (statName == "GYM") isGymDay("GYM") else true
    fun streakOf(snap: VaultSnapshot, statName: String): Int = snap.streaks[statName] ?: 0
    fun doneToday(snap: VaultSnapshot, statName: String): Boolean = snap.done[statName] == todayStr()
    fun displayVal(snap: VaultSnapshot, statName: String): Int = if (doneToday(snap, statName) || snap.streaks[statName] != null) streakOf(snap, statName) else 0

    fun activeErrors(snap: VaultSnapshot): List<String> {
        if (snap === VaultSnapshot.EMPTY) return emptyList()
        val errs = mutableListOf<String>()
        for (s in STATS) {
            if (!isEligible(s.name)) continue
            if (!doneToday(snap, s.name)) errs.add(s.name)
        }
        return errs
    }

    fun dayName(): String = DAY_NAMES[LocalDate.now().dayOfWeek.value]

    fun segBar(count: Int, filled: Int): String {
        val f = filled.coerceIn(0, count)
        return "#".repeat(f) + "\u00b7".repeat(count - f)
    }
}