package com.contactsaver;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSIONS = 100;
    private static final int PICK_FILE = 200;

    // Pages
    private LinearLayout layoutMain, layoutStats, layoutBackup;

    // Main page
    private Button btnPickFile, btnProcess, btnGoStats, btnGoBackup;
    private TextView tvFileName, tvStatus, tvResult;
    private ProgressBar progressBar;
    private LinearLayout layoutResult;

    // Stats page
    private TextView tvStatsTotal, tvStatsDuplicate, tvStatsUnique, tvStatsSource, tvStatsStatus;
    private Button btnDeleteDuplicates, btnBackFromStats;
    private ProgressBar progressStats;

    // Backup page
    private TextView tvBackupInfo, tvBackupStatus;
    private Button btnBackupAll, btnBackupUnique, btnBackFromBackup;
    private ProgressBar progressBackup;

    private Uri selectedFileUri = null;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private List<DuplicateGroup> duplicateGroups = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Pages
        layoutMain = findViewById(R.id.layoutMain);
        layoutStats = findViewById(R.id.layoutStats);
        layoutBackup = findViewById(R.id.layoutBackup);

        // Main
        btnPickFile = findViewById(R.id.btnPickFile);
        btnProcess = findViewById(R.id.btnProcess);
        btnGoStats = findViewById(R.id.btnGoStats);
        btnGoBackup = findViewById(R.id.btnGoBackup);
        tvFileName = findViewById(R.id.tvFileName);
        tvStatus = findViewById(R.id.tvStatus);
        tvResult = findViewById(R.id.tvResult);
        progressBar = findViewById(R.id.progressBar);
        layoutResult = findViewById(R.id.layoutResult);

        // Stats
        tvStatsTotal = findViewById(R.id.tvStatsTotal);
        tvStatsDuplicate = findViewById(R.id.tvStatsDuplicate);
        tvStatsUnique = findViewById(R.id.tvStatsUnique);
        tvStatsSource = findViewById(R.id.tvStatsSource);
        btnDeleteDuplicates = findViewById(R.id.btnDeleteDuplicates);
        btnBackFromStats = findViewById(R.id.btnBackFromStats);
        progressStats = findViewById(R.id.progressStats);
        tvStatsStatus = findViewById(R.id.tvStatsStatus);

        // Backup
        tvBackupInfo = findViewById(R.id.tvBackupInfo);
        tvBackupStatus = findViewById(R.id.tvBackupStatus);
        btnBackupAll = findViewById(R.id.btnBackupAll);
        btnBackupUnique = findViewById(R.id.btnBackupUnique);
        btnBackFromBackup = findViewById(R.id.btnBackFromBackup);
        progressBackup = findViewById(R.id.progressBackup);

        // Listeners Main
        btnPickFile.setOnClickListener(v -> pickFile());
        btnProcess.setOnClickListener(v -> startProcessing());
        btnGoStats.setOnClickListener(v -> openStatsPage());
        btnGoBackup.setOnClickListener(v -> openBackupPage());

        // Listeners Stats
        btnBackFromStats.setOnClickListener(v -> showPage(layoutMain));
        btnDeleteDuplicates.setOnClickListener(v -> confirmDeleteDuplicates());

        // Listeners Backup
        btnBackFromBackup.setOnClickListener(v -> showPage(layoutMain));
        btnBackupAll.setOnClickListener(v -> startBackup(false));
        btnBackupUnique.setOnClickListener(v -> startBackup(true));

        requestPermissions();
    }

    private void showPage(LinearLayout page) {
        layoutMain.setVisibility(View.GONE);
        layoutStats.setVisibility(View.GONE);
        layoutBackup.setVisibility(View.GONE);
        page.setVisibility(View.VISIBLE);
    }

    private void requestPermissions() {
        List<String> needed = new ArrayList<>();
        String[] perms = {
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        };
        for (String p : perms) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED)
                needed.add(p);
        }
        if (!needed.isEmpty())
            ActivityCompat.requestPermissions(this, needed.toArray(new String[0]), REQUEST_PERMISSIONS);
    }

    private void pickFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "Pilih File Kontak"), PICK_FILE);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == PICK_FILE && res == RESULT_OK && data != null) {
            selectedFileUri = data.getData();
            tvFileName.setText("📄 " + getFileName(selectedFileUri));
            btnProcess.setEnabled(true);
            tvStatus.setText("File dipilih. Tap 'Proses Kontak' untuk mulai.");
            layoutResult.setVisibility(View.GONE);
        }
    }

    private String getFileName(Uri uri) {
        String result = uri.getLastPathSegment();
        if (result != null && result.contains("/"))
            result = result.substring(result.lastIndexOf("/") + 1);
        return result != null ? result : "file_kontak";
    }

    // ─── MAIN: IMPORT ────────────────────────────────────────────────────────────

    private void startProcessing() {
        if (selectedFileUri == null) { Toast.makeText(this, "Pilih file dulu!", Toast.LENGTH_SHORT).show(); return; }
        btnProcess.setEnabled(false);
        btnPickFile.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        tvStatus.setText("⏳ Membaca kontak di HP...");
        layoutResult.setVisibility(View.GONE);

        executor.execute(() -> {
            try {
                Set<String> existing = getExistingContactPhones();
                mainHandler.post(() -> tvStatus.setText("⏳ Membaca file..."));
                String fn = getFileName(selectedFileUri).toLowerCase();
                List<ContactEntry> file = fn.endsWith(".vcf") || fn.endsWith(".vcard") ? parseVcf(selectedFileUri) : parseCsv(selectedFileUri);
                mainHandler.post(() -> tvStatus.setText("⏳ Mengecek duplikat..."));
                List<ContactEntry> toSave = new ArrayList<>();
                int dups = 0;
                for (ContactEntry c : file) {
                    boolean dup = false;
                    for (String p : c.phones) { if (existing.contains(normalizePhone(p))) { dup = true; break; } }
                    if (dup) dups++; else toSave.add(c);
                }
                final int total = file.size(), dupCount = dups, uniqueCount = toSave.size();
                mainHandler.post(() -> tvStatus.setText("⏳ Menyimpan " + uniqueCount + " kontak..."));
                int saved = saveContacts(toSave);
                mainHandler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnProcess.setEnabled(true);
                    btnPickFile.setEnabled(true);
                    tvStatus.setText("✅ Selesai!");
                    layoutResult.setVisibility(View.VISIBLE);
                    tvResult.setText("📊 HASIL PROSES\n\n" +
                        "📁 Total di file       : " + total + " kontak\n" +
                        "🔁 Duplikat (dilewati) : " + dupCount + " kontak\n" +
                        "✨ Unik (baru)         : " + uniqueCount + " kontak\n" +
                        "💾 Berhasil disimpan   : " + saved + " kontak");
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnProcess.setEnabled(true);
                    btnPickFile.setEnabled(true);
                    tvStatus.setText("❌ Error: " + e.getMessage());
                });
            }
        });
    }

    // ─── STATS PAGE ──────────────────────────────────────────────────────────────

    private void openStatsPage() {
        showPage(layoutStats);
        tvStatsTotal.setText("⏳ Menghitung...");
        tvStatsDuplicate.setText("⏳");
        tvStatsUnique.setText("⏳");
        tvStatsSource.setText("⏳ Memuat sumber...");
        tvStatsStatus.setText("");
        btnDeleteDuplicates.setEnabled(false);
        progressStats.setVisibility(View.VISIBLE);

        executor.execute(() -> {
            try {
                List<PhoneContact> all = getAllContactsWithSource();
                int total = all.size();
                Map<String, List<PhoneContact>> phoneMap = new LinkedHashMap<>();
                for (PhoneContact c : all) {
                    String norm = normalizePhone(c.phone);
                    if (!norm.isEmpty()) { if (!phoneMap.containsKey(norm)) phoneMap.put(norm, new ArrayList<>()); phoneMap.get(norm).add(c); }
                }
                duplicateGroups.clear();
                int dupCount = 0;
                for (Map.Entry<String, List<PhoneContact>> e : phoneMap.entrySet()) {
                    if (e.getValue().size() > 1) { duplicateGroups.add(new DuplicateGroup(e.getKey(), e.getValue())); dupCount += e.getValue().size() - 1; }
                }
                int uniqueCount = total - dupCount;
                Map<String, Integer> srcMap = new LinkedHashMap<>();
                for (PhoneContact c : all) {
                    String src = c.accountType != null ? c.accountType : "Memori HP";
                    if (src.toLowerCase().contains("google")) src = "Google Account";
                    else if (src.toLowerCase().contains("sim")) src = "SIM Card";
                    else if (src.equals("vnd.sec.contact.phone") || src.equals("Memori HP")) src = "Memori HP";
                    srcMap.put(src, srcMap.getOrDefault(src, 0) + 1);
                }
                StringBuilder sb = new StringBuilder();
                for (Map.Entry<String, Integer> e : srcMap.entrySet()) sb.append("• ").append(e.getKey()).append(": ").append(e.getValue()).append(" kontak\n");
                final int fTotal = total, fDup = dupCount, fUniq = uniqueCount;
                final String fSrc = sb.toString().trim();
                mainHandler.post(() -> {
                    progressStats.setVisibility(View.GONE);
                    tvStatsTotal.setText("📱 Total Kontak: " + fTotal);
                    tvStatsUnique.setText("✨ Unik: " + fUniq + " kontak");
                    tvStatsDuplicate.setText("🔁 Duplikat: " + fDup + " kontak");
                    tvStatsSource.setText("📂 Sumber Kontak:\n" + fSrc);
                    btnDeleteDuplicates.setEnabled(fDup > 0);
                    if (fDup == 0) tvStatsStatus.setText("✅ Tidak ada duplikat!");
                });
            } catch (Exception e) {
                mainHandler.post(() -> { progressStats.setVisibility(View.GONE); tvStatsStatus.setText("❌ Error: " + e.getMessage()); });
            }
        });
    }

    private void confirmDeleteDuplicates() {
        int count = 0;
        for (DuplicateGroup g : duplicateGroups) count += g.contacts.size() - 1;
        final int c = count;
        new AlertDialog.Builder(this)
            .setTitle("⚠️ Hapus Duplikat")
            .setMessage("Akan menghapus " + c + " kontak duplikat.\n\n⚠️ Kontak dari Google Account akan ikut terhapus di server Google.\n\nLanjutkan?")
            .setPositiveButton("Ya, Hapus", (d, w) -> deleteDuplicates())
            .setNegativeButton("Batal", null).show();
    }

    private void deleteDuplicates() {
        btnDeleteDuplicates.setEnabled(false);
        progressStats.setVisibility(View.VISIBLE);
        tvStatsStatus.setText("⏳ Menghapus duplikat...");
        executor.execute(() -> {
            int deleted = 0;
            try {
                for (DuplicateGroup g : duplicateGroups) {
                    for (int i = 1; i < g.contacts.size(); i++) {
                        Uri uri = ContactsContract.RawContacts.CONTENT_URI.buildUpon()
                            .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build();
                        int rows = getContentResolver().delete(uri, ContactsContract.RawContacts._ID + "=?", new String[]{String.valueOf(g.contacts.get(i).rawContactId)});
                        if (rows > 0) deleted++;
                    }
                }
                final int fd = deleted;
                mainHandler.post(() -> { progressStats.setVisibility(View.GONE); tvStatsStatus.setText("✅ Berhasil hapus " + fd + " duplikat!"); duplicateGroups.clear(); openStatsPage(); });
            } catch (Exception e) {
                mainHandler.post(() -> { progressStats.setVisibility(View.GONE); tvStatsStatus.setText("❌ Error: " + e.getMessage()); btnDeleteDuplicates.setEnabled(true); });
            }
        });
    }

    // ─── BACKUP PAGE ─────────────────────────────────────────────────────────────

    private void openBackupPage() {
        showPage(layoutBackup);
        tvBackupStatus.setText("");
        tvBackupInfo.setText("⏳ Menghitung kontak...");
        btnBackupAll.setEnabled(false);
        btnBackupUnique.setEnabled(false);
        progressBackup.setVisibility(View.VISIBLE);

        executor.execute(() -> {
            try {
                List<PhoneContact> all = getAllContactsWithSource();
                int total = all.size();

                // Count uniques
                Map<String, Boolean> seen = new HashMap<>();
                int unique = 0;
                for (PhoneContact c : all) {
                    String norm = normalizePhone(c.phone);
                    if (!seen.containsKey(norm)) { seen.put(norm, true); unique++; }
                }
                final int fTotal = total, fUniq = unique;
                mainHandler.post(() -> {
                    progressBackup.setVisibility(View.GONE);
                    tvBackupInfo.setText(
                        "📋 Info Kontak Saat Ini\n\n" +
                        "📱 Total semua kontak  : " + fTotal + " kontak\n" +
                        "✨ Total kontak unik   : " + fUniq + " kontak\n" +
                        "🔁 Duplikat            : " + (fTotal - fUniq) + " kontak\n\n" +
                        "File backup akan tersimpan di:\nDownloads/ContactSaver/"
                    );
                    btnBackupAll.setEnabled(true);
                    btnBackupUnique.setEnabled(true);
                });
            } catch (Exception e) {
                mainHandler.post(() -> { progressBackup.setVisibility(View.GONE); tvBackupInfo.setText("❌ Error: " + e.getMessage()); });
            }
        });
    }

    private void startBackup(boolean uniqueOnly) {
        btnBackupAll.setEnabled(false);
        btnBackupUnique.setEnabled(false);
        progressBackup.setVisibility(View.VISIBLE);
        tvBackupStatus.setText("⏳ Memuat kontak...");

        executor.execute(() -> {
            try {
                List<PhoneContact> all = getAllContactsWithSource();

                List<PhoneContact> toBackup;
                if (uniqueOnly) {
                    Map<String, Boolean> seen = new HashMap<>();
                    toBackup = new ArrayList<>();
                    for (PhoneContact c : all) {
                        String norm = normalizePhone(c.phone);
                        if (!seen.containsKey(norm)) { seen.put(norm, true); toBackup.add(c); }
                    }
                } else {
                    toBackup = all;
                }

                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                String label = uniqueOnly ? "unik" : "all";

                // Save to Downloads/ContactSaver/
                File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "ContactSaver");
                if (!dir.exists()) dir.mkdirs();

                File csvFile = new File(dir, "backup_" + label + "_" + timestamp + ".csv");
                File vcfFile = new File(dir, "backup_" + label + "_" + timestamp + ".vcf");

                // Write CSV
                mainHandler.post(() -> tvBackupStatus.setText("⏳ Menulis file CSV..."));
                BufferedWriter csvWriter = new BufferedWriter(new FileWriter(csvFile));
                csvWriter.write("nama,nomor,sumber\n");
                for (PhoneContact c : toBackup) {
                    String src = c.accountType != null ? c.accountType : "HP";
                    if (src.toLowerCase().contains("google")) src = "Google";
                    else if (src.toLowerCase().contains("sim")) src = "SIM";
                    else src = "HP";
                    csvWriter.write("\"" + safe(c.name) + "\",\"" + safe(c.phone) + "\",\"" + src + "\"\n");
                }
                csvWriter.close();

                // Write VCF
                mainHandler.post(() -> tvBackupStatus.setText("⏳ Menulis file VCF..."));
                BufferedWriter vcfWriter = new BufferedWriter(new FileWriter(vcfFile));
                for (PhoneContact c : toBackup) {
                    vcfWriter.write("BEGIN:VCARD\n");
                    vcfWriter.write("VERSION:3.0\n");
                    vcfWriter.write("FN:" + safe(c.name) + "\n");
                    vcfWriter.write("TEL;TYPE=CELL:" + safe(c.phone) + "\n");
                    vcfWriter.write("END:VCARD\n\n");
                }
                vcfWriter.close();

                final int count = toBackup.size();
                final String csvPath = csvFile.getAbsolutePath();
                final String vcfPath = vcfFile.getAbsolutePath();

                mainHandler.post(() -> {
                    progressBackup.setVisibility(View.GONE);
                    btnBackupAll.setEnabled(true);
                    btnBackupUnique.setEnabled(true);
                    tvBackupStatus.setText(
                        "✅ Backup selesai!\n\n" +
                        "📦 " + count + " kontak di-backup\n\n" +
                        "📄 CSV: " + csvPath + "\n\n" +
                        "📋 VCF: " + vcfPath
                    );
                });

            } catch (Exception e) {
                mainHandler.post(() -> {
                    progressBackup.setVisibility(View.GONE);
                    btnBackupAll.setEnabled(true);
                    btnBackupUnique.setEnabled(true);
                    tvBackupStatus.setText("❌ Error: " + e.getMessage());
                });
            }
        });
    }

    private String safe(String s) {
        return s == null ? "" : s.replace("\"", "'");
    }

    // ─── HELPERS ─────────────────────────────────────────────────────────────────

    private Set<String> getExistingContactPhones() {
        Set<String> phones = new HashSet<>();
        Cursor c = getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER}, null, null, null);
        if (c != null) { while (c.moveToNext()) { String p = c.getString(0); if (p != null) phones.add(normalizePhone(p)); } c.close(); }
        return phones;
    }

    private List<PhoneContact> getAllContactsWithSource() {
        List<PhoneContact> list = new ArrayList<>();
        Cursor c = getContentResolver().query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            new String[]{
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.RAW_CONTACT_ID,
                ContactsContract.RawContacts.ACCOUNT_TYPE
            }, null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC");
        if (c != null) {
            while (c.moveToNext()) {
                PhoneContact p = new PhoneContact();
                p.name = c.getString(0); p.phone = c.getString(1);
                p.rawContactId = c.getLong(2); p.accountType = c.getString(3);
                list.add(p);
            }
            c.close();
        }
        return list;
    }

    private String normalizePhone(String phone) {
        if (phone == null) return "";
        String s = phone.replaceAll("[^0-9+]", "");
        if (s.startsWith("08")) s = "+628" + s.substring(2);
        if (s.startsWith("628")) s = "+" + s;
        if (s.length() > 9) s = s.substring(s.length() - 9);
        return s;
    }

    private List<ContactEntry> parseVcf(Uri uri) throws Exception {
        List<ContactEntry> list = new ArrayList<>();
        BufferedReader r = new BufferedReader(new InputStreamReader(getContentResolver().openInputStream(uri), "UTF-8"));
        ContactEntry cur = null; String line;
        while ((line = r.readLine()) != null) {
            line = line.trim();
            if (line.equalsIgnoreCase("BEGIN:VCARD")) { cur = new ContactEntry(); }
            else if (line.equalsIgnoreCase("END:VCARD")) { if (cur != null && !cur.phones.isEmpty()) list.add(cur); cur = null; }
            else if (cur != null) {
                if (line.startsWith("FN:")) cur.name = line.substring(3).trim();
                else if (line.startsWith("N:") && cur.name.isEmpty()) {
                    String[] p = line.substring(2).split(";");
                    cur.name = ((p.length > 1 ? p[1] : "") + " " + p[0]).trim();
                } else if (line.startsWith("TEL") && line.contains(":")) {
                    String ph = line.substring(line.indexOf(":") + 1).trim();
                    if (!ph.isEmpty()) cur.phones.add(ph);
                }
            }
        }
        r.close(); return list;
    }

    private List<ContactEntry> parseCsv(Uri uri) throws Exception {
        List<ContactEntry> list = new ArrayList<>();
        BufferedReader r = new BufferedReader(new InputStreamReader(getContentResolver().openInputStream(uri), "UTF-8"));
        String header = r.readLine(); if (header == null) return list;
        String[] headers = header.split(","); int ni = -1, pi = -1;
        for (int i = 0; i < headers.length; i++) {
            String h = headers[i].toLowerCase().replaceAll("[^a-z]", "");
            if (h.contains("name") || h.contains("nama")) ni = i;
            if (h.contains("phone") || h.contains("tel") || h.contains("hp") || h.contains("nomor")) pi = i;
        }
        if (pi == -1) pi = 1; if (ni == -1) ni = 0;
        String line;
        while ((line = r.readLine()) != null) {
            String[] cols = line.split(",", -1);
            if (cols.length <= pi) continue;
            String phone = cols[pi].replaceAll("\"", "").trim();
            String name = ni < cols.length ? cols[ni].replaceAll("\"", "").trim() : phone;
            if (!phone.isEmpty()) { ContactEntry e = new ContactEntry(); e.name = name; e.phones.add(phone); list.add(e); }
        }
        r.close(); return list;
    }

    private int saveContacts(List<ContactEntry> contacts) {
        int saved = 0;
        for (ContactEntry c : contacts) {
            try {
                ArrayList<ContentProviderOperation> ops = new ArrayList<>();
                ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null).build());
                ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, c.name).build());
                for (String phone : c.phones) {
                    ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
                        .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE).build());
                }
                getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
                saved++;
            } catch (Exception ignored) {}
        }
        return saved;
    }

    // ─── MODELS ──────────────────────────────────────────────────────────────────

    static class ContactEntry { String name = ""; List<String> phones = new ArrayList<>(); }
    static class PhoneContact { String name, phone, accountType; long rawContactId; }
    static class DuplicateGroup {
        String normalizedPhone; List<PhoneContact> contacts;
        DuplicateGroup(String p, List<PhoneContact> c) { normalizedPhone = p; contacts = c; }
    }
}