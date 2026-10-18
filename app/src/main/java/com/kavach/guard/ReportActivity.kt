package com.kavach.guard

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.widget.CheckBox
import android.widget.Toast

/** Everything in one place: evidence is already gathered, one tap to each reporting route. */
class ReportActivity : Activity() {
    private var pack: EvidencePack.Pack? = null
    private var numbers: Set<String> = emptySet()
    private var addToList: CheckBox? = null
    private var done = false
    private val lang get() = Prefs(this).lang
    private fun s(k: String) = S.t(this, k, lang)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val items = EvidenceStore.recent(this, 7)
        numbers = CaseStore.numbers(this)
        if (items.isNotEmpty() || numbers.isNotEmpty()) {
            pack = try { EvidencePack.build(this, items, numbers, CaseStore.notes(this), CaseStore.callsFromCase(this)) } catch (e: Exception) { null }
        }
        pack?.let { (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Kavach", it.summary)) }

        val root = Ui.page(this)
        root.addView(Ui.tv(this, s("report_title"), 26f, bold = true))
        val p = pack
        if (p == null) {
            root.addView(Ui.tv(this, s("nothing"), 16f, Color.DKGRAY, top = 12))
        } else {
            root.addView(Ui.tv(this, s("evidence_ready").format(p.count), 16f, Color.parseColor("#1B5E20"), top = 12))
            if (numbers.isNotEmpty())
                root.addView(Ui.tv(this, numbers.joinToString("\n") { "📵 $it" }, 15f, Color.DKGRAY, top = 10))
            addToList = CheckBox(this).apply { text = s("add_to_list"); isChecked = true }
            root.addView(addToList, Ui.lp(this, 10))
            root.addView(Ui.btn(this, s("file_complaint"), "#1B5E20") { open("https://cybercrime.gov.in") }, Ui.lp(this, 14))
            root.addView(Ui.btn(this, s("call1930"), "#B3121D") { dial("1930") }, Ui.lp(this, 10))
            root.addView(Ui.btn(this, s("police"), "#1565C0") { police() }, Ui.lp(this, 10))
            root.addView(Ui.btn(this, s("share_pdf"), "#455A64") { sharePdf(p) }, Ui.lp(this, 10))
        }
        root.addView(Ui.btn(this, s("call112"), "#6A1B9A") { dial("112") }, Ui.lp(this, 22))
        root.addView(Ui.btn(this, s("call1091"), "#AD1457") { dial("1091") }, Ui.lp(this, 10))
        root.addView(Ui.tv(this, s("disclaimer"), 12f, Color.GRAY, top = 18))
        setContentView(Ui.scroll(this, root, Color.WHITE))
    }

    /** Called on any reporting tap: adds the numbers to the existing block list so future calls are stopped. */
    private fun markDone() {
        if (done || addToList?.isChecked != true) return
        done = true
        val prefs = Prefs(this)
        prefs.blockedNumbers = prefs.blockedNumbers + numbers
    }

    private fun open(url: String) { markDone(); try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) { toast("No browser found") } }
    private fun dial(n: String) { markDone(); startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$n"))) }
    private fun police() {
        markDone()
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode("police station")))) }
        catch (_: Exception) { open("https://www.google.com/maps/search/police+station+near+me") }
    }
    private fun sharePdf(p: EvidencePack.Pack) {
        markDone()
        val uri = Uri.parse("content://com.kavach.guard.evidence/" + Uri.encode(p.file.name))
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, uri); putExtra(Intent.EXTRA_TEXT, p.summary)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(i, "Share evidence"))
    }
    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()
}
