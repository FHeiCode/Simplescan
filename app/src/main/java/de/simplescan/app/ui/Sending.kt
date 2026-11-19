package de.simplescan.app.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import de.simplescan.app.data.EmailList
import de.simplescan.app.data.RemoteLink
import de.simplescan.app.data.SavedScan
import de.simplescan.app.data.ShareText

/**
 * Verschickt einen oder mehrere Scans auf einmal.
 * Lokal gespeicherte PDFs gehen als Anhänge raus, hochgeladene als Links im Text.
 *
 * [email] = true: nur E-Mail-Apps. Issue #3: Die Mail-Apps suchen wir selbst (alle, die „mailto:“ öffnen)
 * und schicken die Anhänge gezielt an die, die PDFs annehmen; der frühere „mailto:“-Selektor-Trick
 * funktionierte nicht mit jeder Mail-App. Findet sich keine, kommt das normale Teilen-Menü.
 * [email] = false: normales Teilen-Menü.
 */
internal fun sendScans(context: Context, scans: List<SavedScan>, vm: ScanViewModel, email: Boolean) {
    val local = scans.filter { !isRemote(it) }.filter { exists(context, it, vm) }
    val remote = scans.filter { isRemote(it) }
    if (local.isEmpty() && remote.isEmpty()) return

    vm.linksFor(remote) { links ->
        val subject = ShareText.subject((local + remote).map { it.name })
        val remoteLinks = remote.mapNotNull { s -> links[s.uri]?.let { (link, login) -> RemoteLink(s.name, link, login) } }
        val body = ShareText.body(local.map { it.name }, remoteLinks)
        val uris = ArrayList(local.map { Uri.parse(it.uri) })
        // Feste Empfänger (falls eingestellt) stehen gleich im Feld „An“.
        val recipients = EmailList.split(vm.defaultEmail.value)

        fun attachments(withText: Boolean): Intent? {
            if (uris.isEmpty()) return null
            val intent = if (uris.size == 1) {
                Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            }
            intent.setType("application/pdf")
                .putExtra(Intent.EXTRA_SUBJECT, subject)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (withText) intent.putExtra(Intent.EXTRA_TEXT, body)
            if (email && recipients.isNotEmpty()) intent.putExtra(Intent.EXTRA_EMAIL, recipients.toTypedArray())
            // Über ClipData bekommt die Ziel-App die Leseberechtigung für alle Dateien.
            val clip = ClipData.newRawUri(subject, uris[0])
            uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
            intent.clipData = clip
            return intent
        }

        fun textOnly() = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .putExtra(Intent.EXTRA_TEXT, body)

        if (email) {
            val withFiles = attachments(withText = true)
            if (withFiles == null) {
                // Nur Links: einfache Mail. Betreff und Text stehen zusätzlich in der mailto-Adresse,
                // weil manche Mail-Apps die Extras ignorieren.
                val mailto = Uri.parse(
                    "mailto:" + EmailList.mailtoRecipients(recipients) +
                        "?subject=" + Uri.encode(subject) + "&body=" + Uri.encode(body)
                )
                val mail = Intent(Intent.ACTION_SENDTO, mailto)
                    .putExtra(Intent.EXTRA_SUBJECT, subject)
                    .putExtra(Intent.EXTRA_TEXT, body)
                if (recipients.isNotEmpty()) mail.putExtra(Intent.EXTRA_EMAIL, recipients.toTypedArray())
                startMail(context, listOf(mail), textOnly(), vm)
            } else {
                val targets = mailPackages(context)
                    .mapNotNull { pkg -> attachments(withText = true)?.setPackage(pkg) }
                    .filter { canHandle(context, it) }
                startMail(context, targets, withFiles, vm)
            }
        } else {
            // Beim Teilen nur dann Text mitschicken, wenn Links dabei sind.
            val share = attachments(withText = remoteLinks.isNotEmpty()) ?: textOnly()
            try {
                context.startActivity(Intent.createChooser(share, subject))
            } catch (e: ActivityNotFoundException) {
                vm.show("Keine App zum Teilen gefunden.")
            }
        }
    }
}

/** Pakete aller Apps, die „mailto:“ öffnen, also E-Mail-Apps. Braucht den <queries>-Eintrag im Manifest. */
private fun mailPackages(context: Context): List<String> =
    queryActivities(context, Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")))
        .map { it.activityInfo.packageName }
        .distinct()
        .filter { it != context.packageName }

private fun canHandle(context: Context, intent: Intent) = queryActivities(context, intent).isNotEmpty()

@Suppress("DEPRECATION")
private fun queryActivities(context: Context, intent: Intent): List<ResolveInfo> {
    val pm = context.packageManager
    return if (Build.VERSION.SDK_INT >= 33) {
        pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
    } else {
        pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
    }
}

/**
 * Eine Mail-App → direkt öffnen. Mehrere → Auswahl nur mit diesen Apps.
 * Keine (oder Start scheitert) → normales Teilen-Menü mit [fallback], damit der Nutzer nie festhängt.
 */
private fun startMail(context: Context, targets: List<Intent>, fallback: Intent, vm: ScanViewModel) {
    try {
        when (targets.size) {
            0 -> context.startActivity(Intent.createChooser(fallback, "Senden mit"))
            1 -> context.startActivity(targets[0])
            else -> context.startActivity(
                Intent.createChooser(targets[0], "Per E-Mail senden")
                    .putExtra(Intent.EXTRA_INITIAL_INTENTS, targets.drop(1).toTypedArray())
            )
        }
    } catch (e: ActivityNotFoundException) {
        try {
            context.startActivity(Intent.createChooser(fallback, "Senden mit"))
        } catch (e2: ActivityNotFoundException) {
            vm.show("Keine App zum Versenden gefunden.")
        }
    }
}
