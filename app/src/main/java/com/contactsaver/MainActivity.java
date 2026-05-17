package com.contactsaver;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
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
    private static final String PREFS_NAME = "ContactSaverPrefs";

    // Source type constants
    static final String SRC_GOOGLE   = "Google Account";
    static final String SRC_PHONE    = "Memori HP";
    static final String SRC_SIM      = "SIM Card";
    static final String SRC_WA       = "WhatsApp";
    static final String SRC_WA_BIZ   = "WhatsApp Business";
    static final String SRC_OTHER    = "Lainnya";

    // Pages
    private LinearLayout layoutMain, layoutStats, layoutBackup, layoutSettings;

    // Main page
    private Button btnPickFile, btnAnalyze, btnProcess, btnGoStats, btnGoBackup, btnGoSettings;
    private TextView tvFileName, tvStatus, tvResult, tvAnalyzeStatus, tvAnalyzeResult;
    private ProgressBar progressBar, progressAnalyze;
    private LinearLayout layoutResult, layoutAnalyzeResult;

    // Stats page
    private TextView tvStatsTotal, tvStatsDuplicate, tvStatsUnique, tvStatsSource, tvStatsStatus;
    private Button btnDeleteDuplicates, btnBackFromStats, btnApplyFilter;
    private ProgressBar progressStats;
    private CheckBox chkGoogle, chkPhone, chkSim, chkWa, chkWaBusiness, chkOther;

    // Backup page
    private TextView tvBackupInfo, tvBackupStatus;
    private Button btnBackupAll, btnBackupUnique, btnBackFromBackup;
    private ProgressBar progressBackup;

    // Settings page
    private Button btnBackFromSettings, btnSaveSettings;
    private TextView tvSettingsStatus;
    private Spinner spinnerPriority1, spinnerPriority2, spinnerPriority3, spinnerPriority4, spinnerPriority5;

    private Uri selectedFileUri = null;
    private List<ContactEntry> analyzedToSave = null; // hasil analisis langkah 2

    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private List<DuplicateGroup> duplicateGroups = new ArrayList<>();

    private SharedPreferences prefs;

    // Priority order (index 0 = highest priority = dipertahankan)
    private List<String> priorityOrder = new ArrayList<>(Arrays.asList(
        SRC_GOOGLE, SRC_PHONE, SRC_SIM, SRC_WA_BIZ, SRC_OTHER
    ));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // Pages
        layoutMain     = findViewById(R.id.layoutMain);
        layoutStats    = findViewById(R.id.layoutStats);
        layoutBackup   = findViewById(R.id.layoutBackup);
        layoutSettings = findViewById(R.id.layoutSettings);

        // Main
        btnPickFile          = findViewById(R.id.btnPickFile);
        btnAnalyze           = findViewById(R.id.btnAnalyze);
        btnProcess           = findViewById(R.id.btnProcess);
        btnGoStats           = findViewById(R.id.btnGoStats);
        btnGoBackup          = findViewById(R.id.btnGoBackup);
        btnGoSettings        = findViewById(R.id.btnGoSettings);
        tvFileName           = findViewById(R.id.tvFileName);
        tvStatus             = findViewById(R.id.tvStatus);
        tvResult             = findViewById(R.id.tvResult);
        tvAnalyzeStatus      = findViewById(R.id.tvAnalyzeStatus);
        tvAnalyzeResult      = findViewById(R.id.tvAnalyzeResult);
        progressBar          = findViewById(R.id.progressBar);
        progressAnalyze      = findViewById(R.id.progressAnalyze);
        layoutResult         = findViewById(R.id.layoutResult);
        layoutAnalyzeResult  = findViewById(R.id.layoutAnalyzeResult);

        // Stats
        tvStatsTotal         = findViewById(R.id.tvStatsTotal);
        tvStatsDuplicate     = findViewById(R.id.tvStatsDuplicate);
        tvStatsUnique        = findViewById(R.id.tvStatsUnique);
        tvStatsSource        = findViewById(R.id.tvStatsSource);
        btnDeleteDuplicates  = findViewById(R.id.btnDeleteDuplicates);
        btnBackFromStats     = findViewById(R.id.btnBackFromStats);
        progressStats        = findViewById(R.id.progressStats);
        tvStatsStatus        = findViewById(R.id.tvStatsStatus);
        btnApplyFilter       = findViewById(R.id.btnApplyFilter);
        chkGoogle            = findViewById(R.id.chkGoogle);
        chkPhone             = findViewById(R.id.chkPhone);
        chkSim               = findViewById(R.id.chkSim);
        chkWa                = findViewById(R.id.chkWa);
        chkWaBusiness        = findViewById(R.id.chkWaBusiness);
        chkOther             = findViewById(R.id.chkOther);

        // Backup
        tvBackupInfo         = findViewById(R.id.tvBackupInfo);
        tvBackupStatus       = findViewById(R.id.tvBackupStatus);
        btnBackupAll         = findViewById(R.id.btnBackupAll);
        btnBackupUnique      = findViewById(R.id.btnBackupUnique);
        btnBackFromBackup    = findViewById(R.id.btnBackFromBackup);
        progressBackup       = findViewById(R.id.progressBackup);

        // Settings
        btnBackFromSettings  = findViewById(R.id.btnBackFromSettings);
        btnSaveSettings      = findViewById(R.id.btnSaveSettings);
        tvSettingsStatus     = findViewById(R.id.tvSettingsStatus);
        spinnerPriority1     = findViewById(R.id.spinnerPriority1);
        spinnerPriority2     = findViewById(R.id.spinnerPriority2);
        spinnerPriority3     = findViewById(R.id.spinnerPriority3);
        spinnerPriority4     = findViewById(R.id.spinnerPriority4);
        spinnerPriority5     = findViewById(R.id.spinnerPriority5);

        setupSpinners();
        loadSettings();

        // Listeners Main
        btnPickFile.setOnClickListener(v -> pickFile());
        btnAnalyze.setOnClickListener(v -> startAnalyze());
        btnProcess.setOnClickListener(v -> startProcessing());
        btnGoStats.setOnClickListener(v -> openStatsPage());
        btnGoBackup.setOnClickListener(v -> openBackupPage());
        btnGoSettings.setOnClickListener(v -> openSettingsPage());

        // Listeners Stats
        btnBackFromStats.setOnClickListener(v -> showPage(layoutMain));
        btnDeleteDuplicates.setOnClickListener(v -> confirmDeleteDuplicates());
        btnApplyFilter.setOnClickListener(v -> openStatsPage());

        // Listeners Backup
        btnBackFromBackup.setOnClickListener(v -> showPage(layoutMain));
        btnBackupAll.setOnClickListener(v -> startBackup(false));
        btnBackupUnique.setOnClickListener(v -> startBackup(true));

        // Listeners Settings
        btnBackFromSettings.setOnClickListener(v -> showPage(layoutMain));
        btnSaveSettings.setOnClickListener(v -> saveSettings());

        requestPermissions();
    }

    // ─── SETTINGS ────────────────────────────────────────────────────────────────

    private final String[] SOURCE_OPTIONS = {SRC_GOOGLE, SRC_PHONE, SRC_SIM, SRC_WA, SRC_WA_BIZ, SRC_OTHER};

    private void setupSpinners() {
        Spinner[] spinners = {spinnerPriority1, spinnerPriority2, spinnerPriority3, spinnerPriority4, spinnerPriority5};
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, SOURCE_OPTIONS) {
            @Override public View getView(int pos, View cv, android.view.ViewGroup parent) {
                View v = super.getView(pos, cv, parent); ((TextView)v).setTextColor(0xFFE2E8F0); ((TextView)v).setTextSize(14); return v;
            }
            @Override public View getDropDownView(int pos, View cv, android.view.ViewGroup parent) {
                View v = super.getDropDownView(pos, cv, parent); ((TextView)v).setTextColor(0xFFE2E8F0); ((TextView)v).setBackgroundColor(0xFF1E293B); return v;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        for (Spinner s : spinners) s.setAdapter(adapter);
    }

    private void loadSettings() {
        // Load priority order
        for (int i = 0; i < 5; i++) {
            String val = prefs.getString("priority_" + i, SOURCE_OPTIONS[i]);
            int idx = Arrays.asList(SOURCE_OPTIONS).indexOf(val);
            if (idx < 0) idx = i;
            getSpinner(i).setSelection(idx);
        }
        // Build priorityOrder
        buildPriorityOrderFromSpinners();
    }

    private void saveSettings() {
        // Validate: no duplicates
        Set<String> chosen = new HashSet<>();
        for (int i = 0; i < 5; i++) {
            String val = (String) getSpinner(i).getSelectedItem();
            if (!chosen.add(val)) {
                tvSettingsStatus.setText("❌ Ada sumber yang sama di dua urutan! Pastikan semua berbeda.");
                tvSettingsStatus.setTextColor(0xFFEF4444);
                return;
            }
        }
        SharedPreferences.Editor ed = prefs.edit();
        for (int i = 0; i < 5; i++) ed.putString("priority_" + i, (String) getSpinner(i).getSelectedItem());
        ed.apply();
        buildPriorityOrderFromSpinners();
        tvSettingsStatus.setText("✅ Pengaturan disimpan!");
        tvSettingsStatus.setTextColor(0xFF34D399);
    }

    private void buildPriorityOrderFromSpinners() {
        priorityOrder.clear();
        for (int i = 0; i < 5; i++) priorityOrder.add((String) getSpinner(i).getSelectedItem());
    }

    private Spinner getSpinner(int i) {
        switch(i) {
            case 0: return spinnerPriority1;
            case 1: return spinnerPriority2;
            case 2: return spinnerPriority3;
            case 3: return spinnerPriority4;
            default: return spinnerPriority5;
        }
    }

    // ─── PAGES ───────────────────────────────────────────────────────────────────

    private void showPage(LinearLayout page) {
        layoutMain.setVisibility(View.GONE);
        layoutStats.setVisibility(View.GONE);
        layoutBackup.setVisibility(View.GONE);
        layoutSettings.setVisibility(View.GONE);
        page.setVisibility(View.VISIBLE);
    }

    private void openSettingsPage() {
        showPage(layoutSettings);
        tvSettingsStatus.setText("");
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
            // Reset langkah 2 & 3
            btnAnalyze.setEnabled(true);
            btnProcess.setEnabled(false);
            analyzedToSave = null;
            layoutAnalyzeResult.setVisibility(View.GONE);
            layoutResult.setVisibility(View.GONE);
            tvAnalyzeStatus.setText("File dipilih. Tap 'Analisis File' untuk mengecek.");
            tvStatus.setText("Selesaikan langkah 2 dulu.");
        }
    }

    private String getFileName(Uri uri) {
        String result = uri.getLastPathSegment();
        if (result != null && result.contains("/")) result = result.substring(result.lastIndexOf("/") + 1);
        return result != null ? result : "file_kontak";
    }

    // ─── LANGKAH 2: ANALISIS ─────────────────────────────────────────────────────

    private void startAnalyze() {
        if (selectedFileUri == null) { Toast.makeText(this, "Pilih file dulu!", Toast.LENGTH_SHORT).show(); return; }
        btnAnalyze.setEnabled(false);
        btnProcess.setEnabled(false);
        progressAnalyze.setVisibility(View.VISIBLE);
        layoutAnalyzeResult.setVisibility(View.GONE);
        tvAnalyzeStatus.setText("⏳ Membaca kontak di HP...");

        executor.execute(() -> {
            try {
                Set<String> existing = getExistingContactPhones();
                mainHandler.post(() -> tvAnalyzeStatus.setText("⏳ Membaca file..."));
                String fn = getFileName(selectedFileUri).toLowerCase();
                List<ContactEntry> fileContacts = fn.endsWith(".vcf") || fn.endsWith(".vcard")
                    ? parseVcf(selectedFileUri) : parseCsv(selectedFileUri);

                mainHandler.post(() -> tvAnalyzeStatus.setText("⏳ Menganalisis duplikat..."));
                List<ContactEntry> toSave = new ArrayList<>();
                List<ContactEntry> dupList = new ArrayList<>();
                for (ContactEntry c : fileContacts) {
                    boolean dup = false;
                    for (String p : c.phones) { if (existing.contains(normalizePhone(p))) { dup = true; break; } }
                    if (dup) dupList.add(c); else toSave.add(c);
                }

                final int total = fileContacts.size();
                final int dupCount = dupList.size();
                final int uniqueCount = toSave.size();
                analyzedToSave = toSave; // simpan untuk langkah 3

                mainHandler.post(() -> {
                    progressAnalyze.setVisibility(View.GONE);
                    btnAnalyze.setEnabled(true);
                    btnProcess.setEnabled(uniqueCount > 0);
                    tvAnalyzeStatus.setText("✅ Analisis selesai!");
                    layoutAnalyzeResult.setVisibility(View.VISIBLE);
                    tvAnalyzeResult.setText(
                        "📊 HASIL ANALISIS FILE\n\n" +
                        "📁 Total kontak di file   : " + total + " kontak\n" +
                        "🔁 Sudah ada di HP (skip) : " + dupCount + " kontak\n" +
                        "✨ Baru & unik             : " + uniqueCount + " kontak\n\n" +
                        (uniqueCount > 0
                            ? "➡️ Langkah 3: Tap 'Simpan Kontak Unik'\n   untuk menyimpan " + uniqueCount + " kontak baru."
                            : "ℹ️ Semua kontak di file sudah ada di HP.")
                    );
                    if (uniqueCount == 0) tvStatus.setText("ℹ️ Tidak ada kontak baru untuk disimpan.");
                    else tvStatus.setText("Siap menyimpan " + uniqueCount + " kontak unik.");
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    progressAnalyze.setVisibility(View.GONE);
                    btnAnalyze.setEnabled(true);
                    tvAnalyzeStatus.setText("❌ Error: " + e.getMessage());
                });
            }
        });
    }

    // ─── LANGKAH 3: SIMPAN ───────────────────────────────────────────────────────

    private void startProcessing() {
        if (analyzedToSave == null || analyzedToSave.isEmpty()) {
            Toast.makeText(this, "Lakukan analisis dulu!", Toast.LENGTH_SHORT).show(); return;
        }
        btnProcess.setEnabled(false);
        btnAnalyze.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        tvStatus.setText("⏳ Menyimpan " + analyzedToSave.size() + " kontak...");
        layoutResult.setVisibility(View.GONE);

        final List<ContactEntry> toSave = new ArrayList<>(analyzedToSave);
        executor.execute(() -> {
            try {
                int saved = saveContacts(toSave);
                mainHandler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnProcess.setEnabled(false); // sudah tersimpan, reset
                    btnAnalyze.setEnabled(true);
                    analyzedToSave = null;
                    tvStatus.setText("✅ Selesai!");
                    layoutResult.setVisibility(View.VISIBLE);
                    tvResult.setText(
                        "📊 HASIL SIMPAN\n\n" +
                        "✨ Kontak unik      : " + toSave.size() + " kontak\n" +
                        "💾 Berhasil disimpan: " + saved + " kontak\n" +
                        (saved < toSave.size() ? "⚠️ Gagal simpan     : " + (toSave.size() - saved) + " kontak" : "")
                    );
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnProcess.setEnabled(true);
                    btnAnalyze.setEnabled(true);
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

        // Baca filter checkbox
        final boolean inclGoogle  = chkGoogle.isChecked();
        final boolean inclPhone   = chkPhone.isChecked();
        final boolean inclSim     = chkSim.isChecked();
        final boolean inclWa      = chkWa.isChecked();
        final boolean inclWaBiz   = chkWaBusiness.isChecked();
        final boolean inclOther   = chkOther.isChecked();

        executor.execute(() -> {
            try {
                List<PhoneContact> all = getAllContactsWithSource();

                // Apply filter
                List<PhoneContact> filtered = new ArrayList<>();
                for (PhoneContact c : all) {
                    String src = resolveSource(c.accountType);
                    if (src.equals(SRC_GOOGLE)  && !inclGoogle)  continue;
                    if (src.equals(SRC_PHONE)   && !inclPhone)   continue;
                    if (src.equals(SRC_SIM)     && !inclSim)     continue;
                    if (src.equals(SRC_WA)      && !inclWa)      continue;
                    if (src.equals(SRC_WA_BIZ)  && !inclWaBiz)   continue;
                    if (src.equals(SRC_OTHER)   && !inclOther)   continue;
                    filtered.add(c);
                }

                int total = filtered.size();
                Map<String, List<PhoneContact>> phoneMap = new LinkedHashMap<>();
                for (PhoneContact c : filtered) {
                    String norm = normalizePhone(c.phone);
                    if (!norm.isEmpty()) {
                        if (!phoneMap.containsKey(norm)) phoneMap.put(norm, new ArrayList<>());
                        phoneMap.get(norm).add(c);
                    }
                }
                duplicateGroups.clear();
                int dupCount = 0;
                for (Map.Entry<String, List<PhoneContact>> e : phoneMap.entrySet()) {
                    if (e.getValue().size() > 1) {
                        duplicateGroups.add(new DuplicateGroup(e.getKey(), e.getValue()));
                        dupCount += e.getValue().size() - 1;
                    }
                }
                int uniqueCount = total - dupCount;

                Map<String, Integer> srcMap = new LinkedHashMap<>();
                for (PhoneContact c : filtered) {
                    String src = resolveSource(c.accountType);
                    srcMap.put(src, srcMap.getOrDefault(src, 0) + 1);
                }
                StringBuilder sb = new StringBuilder();
                for (Map.Entry<String, Integer> e : srcMap.entrySet())
                    sb.append("• ").append(e.getKey()).append(": ").append(e.getValue()).append(" kontak\n");

                final int fTotal = total, fDup = dupCount, fUniq = uniqueCount;
                final String fSrc = sb.toString().trim();

                mainHandler.post(() -> {
                    progressStats.setVisibility(View.GONE);
                    tvStatsTotal.setText("📱 Total Kontak (filter): " + fTotal);
                    tvStatsUnique.setText("✨ Unik: " + fUniq + " kontak");
                    tvStatsDuplicate.setText("🔁 Duplikat: " + fDup + " kontak");
                    tvStatsSource.setText("📂 Sumber Kontak:\n" + fSrc);
                    btnDeleteDuplicates.setEnabled(fDup > 0);
                    if (fDup == 0) tvStatsStatus.setText("✅ Tidak ada duplikat!");
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    progressStats.setVisibility(View.GONE);
                    tvStatsStatus.setText("❌ Error: " + e.getMessage());
                });
            }
        });
    }

    private void confirmDeleteDuplicates() {
        int count = 0;
        for (DuplicateGroup g : duplicateGroups) count += g.contacts.size() - 1;
        final int c = count;

        // Build priority info
        StringBuilder pInfo = new StringBuilder("Prioritas hapus (dipertahankan → dihapus):\n");
        for (int i = 0; i < priorityOrder.size(); i++)
            pInfo.append(i + 1).append(". ").append(priorityOrder.get(i)).append("\n");

        new AlertDialog.Builder(this)
            .setTitle("⚠️ Hapus Duplikat")
            .setMessage("Akan menghapus " + c + " kontak duplikat.\n\n" + pInfo + "\n⚠️ Kontak dari Google Account akan ikut terhapus di server Google.\n\nLanjutkan?")
            .setPositiveButton("Ya, Hapus", (d, w) -> deleteDuplicates())
            .setNegativeButton("Batal", null).show();
    }

    private void deleteDuplicates() {
        btnDeleteDuplicates.setEnabled(false);
        progressStats.setVisibility(View.VISIBLE);
        tvStatsStatus.setText("⏳ Menghapus duplikat berdasarkan prioritas...");

        executor.execute(() -> {
            int deleted = 0;
            try {
                for (DuplicateGroup g : duplicateGroups) {
                    // Sort contacts by priority: index 0 = highest priority = keep
                    List<PhoneContact> sorted = new ArrayList<>(g.contacts);
                    sorted.sort((a, b) -> {
                        int ia = priorityOrder.indexOf(resolveSource(a.accountType));
                        int ib = priorityOrder.indexOf(resolveSource(b.accountType));
                        if (ia < 0) ia = priorityOrder.size();
                        if (ib < 0) ib = priorityOrder.size();
                        return Integer.compare(ia, ib);
                    });
                    // Keep index 0, delete the rest
                    for (int i = 1; i < sorted.size(); i++) {
                        Uri uri = ContactsContract.RawContacts.CONTENT_URI.buildUpon()
                            .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build();
                        int rows = getContentResolver().delete(uri,
                            ContactsContract.RawContacts._ID + "=?",
                            new String[]{String.valueOf(sorted.get(i).rawContactId)});
                        if (rows > 0) deleted++;
                    }
                }
                final int fd = deleted;
                mainHandler.post(() -> {
                    progressStats.setVisibility(View.GONE);
                    tvStatsStatus.setText("✅ Berhasil hapus " + fd + " duplikat!");
                    duplicateGroups.clear();
                    openStatsPage();
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    progressStats.setVisibility(View.GONE);
                    tvStatsStatus.setText("❌ Error: " + e.getMessage());
                    btnDeleteDuplicates.setEnabled(true);
                });
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
                mainHandler.post(() -> {
                    progressBackup.setVisibility(View.GONE);
                    tvBackupInfo.setText("❌ Error: " + e.getMessage());
                });
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

                File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "ContactSaver");
                if (!dir.exists()) dir.mkdirs();

                File csvFile = new File(dir, "backup_" + label + "_" + timestamp + ".csv");
                File vcfFile = new File(dir, "backup_" + label + "_" + timestamp + ".vcf");

                mainHandler.post(() -> tvBackupStatus.setText("⏳ Menulis file CSV..."));
                BufferedWriter csvWriter = new BufferedWriter(new FileWriter(csvFile));
                csvWriter.write("nama,nomor,sumber\n");
                for (PhoneContact c : toBackup) {
                    String src = resolveSource(c.accountType);
                    csvWriter.write("\"" + safe(c.name) + "\",\"" + safe(c.phone) + "\",\"" + src + "\"\n");
                }
                csvWriter.close();

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

    // ─── HELPERS ─────────────────────────────────────────────────────────────────

    /** Resolve accountType string → friendly source name */
    private String resolveSource(String accountType) {
        if (accountType == null) return SRC_PHONE;
        String t = accountType.toLowerCase();
        if (t.contains("google"))                   return SRC_GOOGLE;
        if (t.contains("sim"))                      return SRC_SIM;
        if (t.equals("com.whatsapp.w4b"))           return SRC_WA_BIZ;
        if (t.contains("whatsapp"))                 return SRC_WA;
        if (t.equals("vnd.sec.contact.phone") || accountType.equals(SRC_PHONE)) return SRC_PHONE;
        if (t.equals("phone"))                      return SRC_PHONE;
        return SRC_OTHER;
    }

    private Set<String> getExistingContactPhones() {
        Set<String> phones = new HashSet<>();
        Cursor c = getContentResolver().query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER}, null, null, null);
        if (c != null) {
            while (c.moveToNext()) { String p = c.getString(0); if (p != null) phones.add(normalizePhone(p)); }
            c.close();
        }
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

    private String safe(String s) { return s == null ? "" : s.replace("\"", "'"); }

    // ─── MODELS ──────────────────────────────────────────────────────────────────

    static class ContactEntry { String name = ""; List<String> phones = new ArrayList<>(); }
    static class PhoneContact { String name, phone, accountType; long rawContactId; }
    static class DuplicateGroup {
        String normalizedPhone; List<PhoneContact> contacts;
        DuplicateGroup(String p, List<PhoneContact> c) { normalizedPhone = p; contacts = c; }
    }
}
