package com.kavach.guard

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout

/** Unwanted / threatening calls from several numbers: gather them into one case, then one tap to report. */
class HarassmentActivity : Activity() {
    private lateinit var list: LinearLayout
    private lateinit var input: EditText
    private lateinit var notes: EditText
    private val lang get() = Prefs(this).lang
    private fun s(k: String) = S.t(this, k, lang)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = Ui.page(this)
        root.addView(Ui.tv(this, s("harass_title"), 24f, bold = true))
        root.addView(Ui.tv(this, s("harass_hint"), 14f, Color.DKGRAY, top = 8))
        input = EditText(this).apply { hint = "98765 43210, 98765 11111"; inputType = InputType.TYPE_CLASS_TEXT; minLines = 2 }
        root.addView(input, Ui.lp(this, 12))
        root.addView(Ui.btn(this, s("add"), "#455A64") { addTyped() }, Ui.lp(this, 6))
        root.addView(Ui.btn(this, s("import_log"), "#1565C0") { importLog() }, Ui.lp(this, 8))
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(list, Ui.lp(this, 12))
        notes = EditText(this).apply { hint = s("notes_hint"); setText(CaseStore.notes(this@HarassmentActivity)); minLines = 2 }
        root.addView(notes, Ui.lp(this, 8))
        root.addView(Ui.btn(this, s("report_now"), "#B3121D") {
            CaseStore.setNotes(this, notes.text.toString())
            startActivity(Intent(this, ReportActivity::class.java))
        }, Ui.lp(this, 16))
        root.addView(Ui.btn(this, s("call112"), "#6A1B9A") { startActivity(Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:112"))) }, Ui.lp(this, 10))
        setContentView(Ui.scroll(this, root, Color.WHITE))
        refresh()
    }

    private fun addTyped() { CaseStore.add(this, input.text.toString()); input.setText(""); refresh() }

    private fun refresh() {
        list.removeAllViews()
        CaseStore.numbers(this).sorted().forEach { n ->
            list.addView(Ui.btn(this, "📵 $n   ✕", "#EEEEEE", Color.BLACK) { CaseStore.remove(this, n); refresh() }, Ui.lp(this, 6))
        }
    }

    private fun importLog() {
        if (!CaseStore.hasCallLogAccess(this)) { requestPermissions(arrayOf(Manifest.permission.READ_CALL_LOG), 9); return }
        val callers = CaseStore.unsavedCallers(this)
        if (callers.isEmpty()) { android.widget.Toast.makeText(this, "No unsaved callers found", android.widget.Toast.LENGTH_SHORT).show(); return }
        val picked = BooleanArray(callers.size)
        AlertDialog.Builder(this).setTitle(s("harass_title"))
            .setMultiChoiceItems(callers.map { "${it.first}  (${it.second}x)" }.toTypedArray(), picked) { _, i, on -> picked[i] = on }
            .setPositiveButton(s("add")) { _, _ ->
                CaseStore.addAll(this, callers.filterIndexed { i, _ -> picked[i] }.map { it.first }); refresh()
            }.setNegativeButton(s("cancel"), null).show()
    }

    override fun onRequestPermissionsResult(code: Int, p: Array<out String>, r: IntArray) {
        if (code == 9 && r.firstOrNull() == android.content.pm.PackageManager.PERMISSION_GRANTED) importLog()
    }
}
