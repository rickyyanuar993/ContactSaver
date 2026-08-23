package com.contactsaver;

import android.Manifest;
import android.accounts.AccountManager;
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

    // Main
    private Button btnPickFile, btnAnalyze, btnProcess, btnPreviewImport, btnGoStats, btnGoBackup, btnGoSettings, btnGoDeleteByFile;
    private TextView tvFileName, tvStatus, tvResult, tvAnalyzeStatus, tvAnalyzeResult, tvSaveDestNote;
    private ProgressBar progressBar, progressAnalyze;
    private LinearLayout layoutResult, layoutAnalyzeResult, layoutGoogleAccountPicker, layoutAnalyzeGoogleAccountPicker;
    private CheckBox chkAnalyzeGoogle, chkAnalyzePhone, chkAnalyzeSim, chkAnalyzeWa, chkAnalyzeWaBiz;
    private CheckBox chkExcludeSuspicious;
    private Spinner spinnerCountryFilter;
    private android.widget.RadioGroup rgSaveDestination;
    private android.widget.RadioButton rbSavePhone, rbSaveGoogle;
    private Spinner spinnerGoogleAccount, spinnerAnalyzeGoogleAccount;
    private List<android.accounts.Account> googleAccounts = new ArrayList<>();
    private List<String> googleAccountNames = new ArrayList<>();

    // Delete By File page
    private LinearLayout layoutDeleteByFile, layoutDeleteByFileResult, layoutDelFileGoogleAccountPicker;
    private Button btnBackFromDeleteByFile, btnPickFileForDelete, btnAnalyzeFileForDelete, btnPreviewDeleteByFile, btnExecuteDeleteByFile;
    private TextView tvFileNameForDelete, tvDeleteByFileResult, tvDeleteByFileStatus;
    private ProgressBar progressDeleteByFile;
    private Spinner spinnerDelFileGoogleAccount;
    private CheckBox chkDelFileGoogle, chkDelFilePhone, chkDelFileSim, chkDelFileWa, chkDelFileWaBusiness, chkDelFileOther;
    private Uri selectedDeleteFileUri = null;
    private List<PhoneContact> contactsToDeleteByFile = new ArrayList<>();
    private static final int REQ_PICK_FILE_FOR_DELETE = 1002;

    // Stats page
    private TextView tvStatsTotal, tvStatsDuplicate, tvStatsUnique, tvStatsSource, tvStatsStatus;
    private Button btnDeleteDuplicates, btnDeleteAll, btnBackupFilterAll, btnBackupFilterUnique, btnBackFromStats, btnApplyFilter, btnViewContacts;
    private ProgressBar progressStats;
    private LinearLayout layoutStatsGoogleAccountPicker;
    private Spinner spinnerStatsGoogleAccount;
    private CheckBox chkGoogle, chkPhone, chkSim, chkWa, chkWaBusiness, chkOther;

    // Check Single Phone (Stats page)
    private EditText etCheckPhoneNumber;
    private Button btnCheckSinglePhone;
    private ProgressBar progressCheckPhone;
    private LinearLayout layoutCheckPhoneResult;
    private TextView tvCheckPhoneResultHeader, tvCheckPhoneResultDetails;

    // Filtered contacts list (for "Lihat Kontak" dialog)
    private List<PhoneContact> lastFilteredContacts = new ArrayList<>();

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
    private List<ContactEntry> allParsedContacts = null;
    private Set<String> existingPhonesForPreview = null;
    private String detectedFormat = "";
    private String lastAnalyzeSrcLabel = "";

    // CSV column mapping state
    private LinearLayout layoutCsvColumnMapping;
    private Spinner spinnerCsvNameCol, spinnerCsvPhoneCol;
    private CheckBox chkCsvHasHeader;
    private Button btnViewSampleCsv;
    private List<List<String>> rawCsvRows = new ArrayList<>();
    private boolean isApplyingMapping = false;
    private int detectedNameCol = 0, detectedPhoneCol = 1;
    private boolean detectedHasHeader = true;

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
        btnPreviewImport     = findViewById(R.id.btnPreviewImport);
        btnGoStats           = findViewById(R.id.btnGoStats);
        btnGoBackup          = findViewById(R.id.btnGoBackup);
        btnGoSettings        = findViewById(R.id.btnGoSettings);
        tvFileName           = findViewById(R.id.tvFileName);
        tvStatus             = findViewById(R.id.tvStatus);
        tvResult             = findViewById(R.id.tvResult);
        tvAnalyzeStatus      = findViewById(R.id.tvAnalyzeStatus);
        tvAnalyzeResult      = findViewById(R.id.tvAnalyzeResult);
        tvSaveDestNote       = findViewById(R.id.tvSaveDestNote);
        progressBar          = findViewById(R.id.progressBar);
        progressAnalyze      = findViewById(R.id.progressAnalyze);
        layoutResult         = findViewById(R.id.layoutResult);
        layoutAnalyzeResult  = findViewById(R.id.layoutAnalyzeResult);
        layoutGoogleAccountPicker = findViewById(R.id.layoutGoogleAccountPicker);
        layoutAnalyzeGoogleAccountPicker = findViewById(R.id.layoutAnalyzeGoogleAccountPicker);
        spinnerAnalyzeGoogleAccount      = findViewById(R.id.spinnerAnalyzeGoogleAccount);
        chkAnalyzeGoogle     = findViewById(R.id.chkAnalyzeGoogle);
        chkAnalyzePhone      = findViewById(R.id.chkAnalyzePhone);
        chkAnalyzeSim        = findViewById(R.id.chkAnalyzeSim);
        chkAnalyzeWa         = findViewById(R.id.chkAnalyzeWa);
        chkAnalyzeWaBiz      = findViewById(R.id.chkAnalyzeWaBiz);
        rgSaveDestination    = findViewById(R.id.rgSaveDestination);
        rbSavePhone          = findViewById(R.id.rbSavePhone);
        rbSaveGoogle         = findViewById(R.id.rbSaveGoogle);
        spinnerGoogleAccount = findViewById(R.id.spinnerGoogleAccount);

        chkAnalyzeGoogle.setOnCheckedChangeListener((btn, isChecked) -> {
            if (layoutAnalyzeGoogleAccountPicker != null)
                layoutAnalyzeGoogleAccountPicker.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        // Stats
        tvStatsTotal         = findViewById(R.id.tvStatsTotal);
        tvStatsDuplicate     = findViewById(R.id.tvStatsDuplicate);
        tvStatsUnique        = findViewById(R.id.tvStatsUnique);
        tvStatsSource        = findViewById(R.id.tvStatsSource);
        btnDeleteDuplicates  = findViewById(R.id.btnDeleteDuplicates);
        btnDeleteAll         = findViewById(R.id.btnDeleteAll);
        btnBackupFilterAll   = findViewById(R.id.btnBackupFilterAll);
        btnBackupFilterUnique= findViewById(R.id.btnBackupFilterUnique);
        btnBackFromStats     = findViewById(R.id.btnBackFromStats);
        progressStats        = findViewById(R.id.progressStats);
        tvStatsStatus        = findViewById(R.id.tvStatsStatus);
        btnApplyFilter       = findViewById(R.id.btnApplyFilter);
        btnViewContacts      = findViewById(R.id.btnViewContacts);
        layoutStatsGoogleAccountPicker = findViewById(R.id.layoutStatsGoogleAccountPicker);
        spinnerStatsGoogleAccount      = findViewById(R.id.spinnerStatsGoogleAccount);
        chkGoogle            = findViewById(R.id.chkGoogle);
        chkPhone             = findViewById(R.id.chkPhone);
        chkSim               = findViewById(R.id.chkSim);
        chkWa                = findViewById(R.id.chkWa);
        chkWaBusiness        = findViewById(R.id.chkWaBusiness);
        chkOther             = findViewById(R.id.chkOther);

        // Check Single Phone
        etCheckPhoneNumber       = findViewById(R.id.etCheckPhoneNumber);
        btnCheckSinglePhone      = findViewById(R.id.btnCheckSinglePhone);
        progressCheckPhone       = findViewById(R.id.progressCheckPhone);
        layoutCheckPhoneResult   = findViewById(R.id.layoutCheckPhoneResult);
        tvCheckPhoneResultHeader = findViewById(R.id.tvCheckPhoneResultHeader);
        tvCheckPhoneResultDetails= findViewById(R.id.tvCheckPhoneResultDetails);

        chkGoogle.setOnCheckedChangeListener((btn, isChecked) -> {
            if (layoutStatsGoogleAccountPicker != null)
                layoutStatsGoogleAccountPicker.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

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
        loadGoogleAccounts();

        // Save destination toggle
        rgSaveDestination.setOnCheckedChangeListener((group, checkedId) -> {
            boolean isGoogle = (checkedId == R.id.rbSaveGoogle);
            layoutGoogleAccountPicker.setVisibility(isGoogle ? View.VISIBLE : View.GONE);
            tvSaveDestNote.setText(isGoogle
                ? "⚠️ Kontak akan sync ke Google — pastikan kamu pilih akun yang benar."
                : "💡 Kontak tersimpan lokal di HP, tidak otomatis sync ke Google.");
        });

        // CSV Mapping
        layoutCsvColumnMapping   = findViewById(R.id.layoutCsvColumnMapping);
        spinnerCsvNameCol        = findViewById(R.id.spinnerCsvNameCol);
        spinnerCsvPhoneCol       = findViewById(R.id.spinnerCsvPhoneCol);
        chkCsvHasHeader          = findViewById(R.id.chkCsvHasHeader);
        btnViewSampleCsv         = findViewById(R.id.btnViewSampleCsv);

        btnViewSampleCsv.setOnClickListener(v -> showSampleCsvDialog());

        android.widget.AdapterView.OnItemSelectedListener csvMappingListener = new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (!isApplyingMapping && rawCsvRows != null && !rawCsvRows.isEmpty()) {
                    reapplyCsvColumnMapping();
                }
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        };
        spinnerCsvNameCol.setOnItemSelectedListener(csvMappingListener);
        spinnerCsvPhoneCol.setOnItemSelectedListener(csvMappingListener);
        chkCsvHasHeader.setOnCheckedChangeListener((btn, isChecked) -> {
            if (!isApplyingMapping && rawCsvRows != null && !rawCsvRows.isEmpty()) {
                reapplyCsvColumnMapping();
            }
        });

        // Smart Filters
        chkExcludeSuspicious = findViewById(R.id.chkExcludeSuspicious);
        spinnerCountryFilter = findViewById(R.id.spinnerCountryFilter);

        // Delete By File
        layoutDeleteByFile               = findViewById(R.id.layoutDeleteByFile);
        layoutDeleteByFileResult         = findViewById(R.id.layoutDeleteByFileResult);
        layoutDelFileGoogleAccountPicker = findViewById(R.id.layoutDelFileGoogleAccountPicker);
        btnBackFromDeleteByFile          = findViewById(R.id.btnBackFromDeleteByFile);
        btnPickFileForDelete             = findViewById(R.id.btnPickFileForDelete);
        btnAnalyzeFileForDelete          = findViewById(R.id.btnAnalyzeFileForDelete);
        btnPreviewDeleteByFile           = findViewById(R.id.btnPreviewDeleteByFile);
        btnExecuteDeleteByFile           = findViewById(R.id.btnExecuteDeleteByFile);
        btnGoDeleteByFile                = findViewById(R.id.btnGoDeleteByFile);
        tvFileNameForDelete              = findViewById(R.id.tvFileNameForDelete);
        tvDeleteByFileResult             = findViewById(R.id.tvDeleteByFileResult);
        tvDeleteByFileStatus             = findViewById(R.id.tvDeleteByFileStatus);
        progressDeleteByFile             = findViewById(R.id.progressDeleteByFile);
        spinnerDelFileGoogleAccount      = findViewById(R.id.spinnerDelFileGoogleAccount);
        chkDelFileGoogle                 = findViewById(R.id.chkDelFileGoogle);
        chkDelFilePhone                  = findViewById(R.id.chkDelFilePhone);
        chkDelFileSim                    = findViewById(R.id.chkDelFileSim);
        chkDelFileWa                     = findViewById(R.id.chkDelFileWa);
        chkDelFileWaBusiness             = findViewById(R.id.chkDelFileWaBusiness);
        chkDelFileOther                  = findViewById(R.id.chkDelFileOther);

        chkDelFileGoogle.setOnCheckedChangeListener((btn, isChecked) -> {
            if (layoutDelFileGoogleAccountPicker != null)
                layoutDelFileGoogleAccountPicker.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        // Listeners Main
        btnPickFile.setOnClickListener(v -> pickFile());
        btnAnalyze.setOnClickListener(v -> startAnalyze());
        btnProcess.setOnClickListener(v -> startProcessing());
        btnPreviewImport.setOnClickListener(v -> showImportPreviewDialog());
        btnGoStats.setOnClickListener(v -> openStatsPage());
        btnGoBackup.setOnClickListener(v -> openBackupPage());
        btnGoSettings.setOnClickListener(v -> openSettingsPage());
        btnGoDeleteByFile.setOnClickListener(v -> openDeleteByFilePage());

        // Listeners Delete By File
        btnBackFromDeleteByFile.setOnClickListener(v -> showPage(layoutMain));
        btnPickFileForDelete.setOnClickListener(v -> pickFileForDelete());
        btnAnalyzeFileForDelete.setOnClickListener(v -> analyzeFileForDelete());
        btnPreviewDeleteByFile.setOnClickListener(v -> showDeleteByFilePreviewDialog());
        btnExecuteDeleteByFile.setOnClickListener(v -> executeDeleteByFile());

        // Listeners Stats
        btnBackFromStats.setOnClickListener(v -> showPage(layoutMain));
        btnCheckSinglePhone.setOnClickListener(v -> checkSinglePhoneNumber());
        etCheckPhoneNumber.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH || actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                checkSinglePhoneNumber();
                return true;
            }
            return false;
        });
        btnDeleteDuplicates.setOnClickListener(v -> confirmDeleteDuplicates());
        btnDeleteAll.setOnClickListener(v -> confirmDeleteAll());
        btnBackupFilterAll.setOnClickListener(v -> startBackupFiltered(false));
        btnBackupFilterUnique.setOnClickListener(v -> startBackupFiltered(true));
        btnApplyFilter.setOnClickListener(v -> calculateStats());
        btnViewContacts.setOnClickListener(v -> showContactListDialog());

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

        // Country Filter
        String[] countryOptions = {
            "🌐 Semua Negara (Valid)",
            "🇮🇩 Hanya Indonesia (+62 / 08)",
            "🇮🇩 Indonesia (+62) & 🇲🇾 Malaysia (+60)"
        };
        ArrayAdapter<String> countryAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, countryOptions) {
            @Override public View getView(int pos, View cv, android.view.ViewGroup parent) {
                View v = super.getView(pos, cv, parent); ((TextView)v).setTextColor(0xFFE2E8F0); ((TextView)v).setTextSize(12); return v;
            }
            @Override public View getDropDownView(int pos, View cv, android.view.ViewGroup parent) {
                View v = super.getDropDownView(pos, cv, parent); ((TextView)v).setTextColor(0xFFE2E8F0); ((TextView)v).setBackgroundColor(0xFF1E293B); return v;
            }
        };
        countryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        if (spinnerCountryFilter != null) {
            spinnerCountryFilter.setAdapter(countryAdapter);
            spinnerCountryFilter.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                    if (rawCsvRows != null && !rawCsvRows.isEmpty()) reapplyCsvColumnMapping();
                }
                @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
            });
        }
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
        if (layoutDeleteByFile != null) layoutDeleteByFile.setVisibility(View.GONE);
        page.setVisibility(View.VISIBLE);
    }

    @Override
    public void onBackPressed() {
        if ((layoutStats != null && layoutStats.getVisibility() == View.VISIBLE)
                || (layoutBackup != null && layoutBackup.getVisibility() == View.VISIBLE)
                || (layoutSettings != null && layoutSettings.getVisibility() == View.VISIBLE)
                || (layoutDeleteByFile != null && layoutDeleteByFile.getVisibility() == View.VISIBLE)) {
            showPage(layoutMain);
        } else {
            new AlertDialog.Builder(this)
                .setTitle("⚠️ Keluar Aplikasi")
                .setMessage("Apakah Anda yakin ingin keluar dari aplikasi Contact Saver?")
                .setPositiveButton("Ya, Keluar", (dialog, which) -> finishAffinity())
                .setNegativeButton("Batal", null)
                .show();
        }
    }

    private void openSettingsPage() {
        showPage(layoutSettings);
        tvSettingsStatus.setText("");
    }

    private void loadGoogleAccounts() {
        try {
            Set<String> namesSet = new LinkedHashSet<>();
            try {
                android.accounts.Account[] accounts = AccountManager.get(this)
                    .getAccountsByType("com.google");
                googleAccounts.clear();
                if (accounts != null) {
                    googleAccounts.addAll(Arrays.asList(accounts));
                    for (android.accounts.Account a : accounts) namesSet.add(a.name);
                }
            } catch (Exception ignored) {}

            // Fallback / supplement: query distinct Google accounts from Contacts Provider
            if (namesSet.isEmpty()) {
                try {
                    Cursor c = getContentResolver().query(
                        ContactsContract.RawContacts.CONTENT_URI,
                        new String[]{ContactsContract.RawContacts.ACCOUNT_NAME},
                        ContactsContract.RawContacts.ACCOUNT_TYPE + "=?",
                        new String[]{"com.google"},
                        null
                    );
                    if (c != null) {
                        while (c.moveToNext()) {
                            String name = c.getString(0);
                            if (name != null && !name.trim().isEmpty()) {
                                namesSet.add(name.trim());
                            }
                        }
                        c.close();
                    }
                } catch (Exception ignored) {}
            }

            googleAccountNames.clear();
            googleAccountNames.addAll(namesSet);

            if (googleAccountNames.isEmpty()) {
                rbSaveGoogle.setEnabled(false);
                rbSaveGoogle.setText("☁️ Google Account (tidak ada akun Google di HP)");
            } else {
                String[] names = googleAccountNames.toArray(new String[0]);
                ArrayAdapter<String> saveAdapter = new ArrayAdapter<String>(this,
                        android.R.layout.simple_spinner_item, names) {
                    @Override public View getView(int pos, View cv, android.view.ViewGroup p) {
                        View v = super.getView(pos, cv, p);
                        ((TextView)v).setTextColor(0xFFE2E8F0); ((TextView)v).setTextSize(13); return v;
                    }
                    @Override public View getDropDownView(int pos, View cv, android.view.ViewGroup p) {
                        View v = super.getDropDownView(pos, cv, p);
                        ((TextView)v).setTextColor(0xFFE2E8F0); ((TextView)v).setBackgroundColor(0xFF1E293B); return v;
                    }
                };
                saveAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinnerGoogleAccount.setAdapter(saveAdapter);

                // Setup filter dropdown (with "🌐 Semua Akun Google" at index 0)
                String[] filterOptions = new String[googleAccountNames.size() + 1];
                filterOptions[0] = "🌐 Semua Akun Google";
                for (int i = 0; i < googleAccountNames.size(); i++) {
                    filterOptions[i + 1] = "📧 " + googleAccountNames.get(i);
                }

                ArrayAdapter<String> filterAdapter = new ArrayAdapter<String>(this,
                        android.R.layout.simple_spinner_item, filterOptions) {
                    @Override public View getView(int pos, View cv, android.view.ViewGroup p) {
                        View v = super.getView(pos, cv, p);
                        ((TextView)v).setTextColor(0xFFE2E8F0); ((TextView)v).setTextSize(12); return v;
                    }
                    @Override public View getDropDownView(int pos, View cv, android.view.ViewGroup p) {
                        View v = super.getDropDownView(pos, cv, p);
                        ((TextView)v).setTextColor(0xFFE2E8F0); ((TextView)v).setBackgroundColor(0xFF1E293B); return v;
                    }
                };
                filterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                if (spinnerAnalyzeGoogleAccount != null) spinnerAnalyzeGoogleAccount.setAdapter(filterAdapter);
                if (spinnerStatsGoogleAccount != null) spinnerStatsGoogleAccount.setAdapter(filterAdapter);
                if (spinnerDelFileGoogleAccount != null) spinnerDelFileGoogleAccount.setAdapter(filterAdapter);
            }
        } catch (Exception e) {
            rbSaveGoogle.setEnabled(false);
        }
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
            allParsedContacts = null;
            existingPhonesForPreview = null;
            rawCsvRows.clear();
            detectedFormat = "";
            if (layoutCsvColumnMapping != null) layoutCsvColumnMapping.setVisibility(View.GONE);
            if (btnViewSampleCsv != null) btnViewSampleCsv.setVisibility(View.GONE);
            layoutAnalyzeResult.setVisibility(View.GONE);
            layoutResult.setVisibility(View.GONE);
            tvAnalyzeStatus.setText("File dipilih. Tap 'Analisis File' untuk mengecek.");
            tvStatus.setText("Selesaikan langkah 2 dulu.");
        } else if (req == REQ_PICK_FILE_FOR_DELETE && res == RESULT_OK && data != null) {
            selectedDeleteFileUri = data.getData();
            tvFileNameForDelete.setText("📄 " + getFileName(selectedDeleteFileUri));
            btnAnalyzeFileForDelete.setEnabled(true);
            btnPreviewDeleteByFile.setEnabled(false);
            btnExecuteDeleteByFile.setEnabled(false);
            layoutDeleteByFileResult.setVisibility(View.GONE);
            tvDeleteByFileStatus.setText("File dipilih. Tap 'Cari Kontak Cocok di HP' untuk menganalisis.");
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

        final boolean inclGoogle = chkAnalyzeGoogle.isChecked();
        final boolean inclPhone  = chkAnalyzePhone.isChecked();
        final boolean inclSim    = chkAnalyzeSim.isChecked();
        final boolean inclWa     = chkAnalyzeWa.isChecked();
        final boolean inclWaBiz  = chkAnalyzeWaBiz.isChecked();

        final int selAnalyzeGoogle = (spinnerAnalyzeGoogleAccount != null && spinnerAnalyzeGoogleAccount.getSelectedItemPosition() > 0)
            ? spinnerAnalyzeGoogleAccount.getSelectedItemPosition() : 0;
        final String selectedAnalyzeGoogleAccountName = (selAnalyzeGoogle > 0 && selAnalyzeGoogle - 1 < googleAccountNames.size())
            ? googleAccountNames.get(selAnalyzeGoogle - 1) : null;

        executor.execute(() -> {
            try {
                // Baca semua kontak lalu filter sesuai pilihan user
                List<PhoneContact> allContacts = getAllContactsWithSource();
                Set<String> existing = new HashSet<>();
                for (PhoneContact c : allContacts) {
                    String src = resolveSource(c.accountType);
                    if (src.equals(SRC_GOOGLE)) {
                        if (!inclGoogle) continue;
                        if (selectedAnalyzeGoogleAccountName != null && (c.accountName == null || !c.accountName.equalsIgnoreCase(selectedAnalyzeGoogleAccountName))) continue;
                    }
                    if (src.equals(SRC_PHONE)   && !inclPhone)  continue;
                    if (src.equals(SRC_SIM)     && !inclSim)    continue;
                    if (src.equals(SRC_WA)      && !inclWa)     continue;
                    if (src.equals(SRC_WA_BIZ)  && !inclWaBiz)  continue;
                    if (c.phone != null) existing.add(normalizePhone(c.phone));
                }

                // Build label sumber yang dicek
                List<String> srcChecked = new ArrayList<>();
                if (inclGoogle) {
                    srcChecked.add(selectedAnalyzeGoogleAccountName != null ? "Google (" + selectedAnalyzeGoogleAccountName + ")" : "Google (Semua Akun)");
                }
                if (inclPhone)  srcChecked.add("HP");
                if (inclSim)    srcChecked.add("SIM");
                if (inclWa)     srcChecked.add("WA");
                if (inclWaBiz)  srcChecked.add("WA Bisnis");
                final String srcLabel = srcChecked.isEmpty() ? "tidak ada" : String.join(", ", srcChecked);

                mainHandler.post(() -> tvAnalyzeStatus.setText("⏳ Membaca file..."));
                String fn = getFileName(selectedFileUri).toLowerCase();
                boolean isVcf = fn.endsWith(".vcf") || fn.endsWith(".vcard");
                if (isVcf) {
                    detectedFormat = "VCF (vCard) File";
                }
                List<ContactEntry> fileContacts = isVcf
                    ? parseVcf(selectedFileUri) : parseCsv(selectedFileUri);

                mainHandler.post(() -> tvAnalyzeStatus.setText("⏳ Menganalisis duplikat & validasi..."));
                final boolean excludeSuspicious = (chkExcludeSuspicious == null || chkExcludeSuspicious.isChecked());
                final int countryFilterPos = (spinnerCountryFilter != null) ? spinnerCountryFilter.getSelectedItemPosition() : 0;

                List<ContactEntry> toSave = new ArrayList<>();
                List<ContactEntry> dupList = new ArrayList<>();
                List<ContactEntry> suspiciousList = new ArrayList<>();

                for (ContactEntry c : fileContacts) {
                    boolean isSusp = false;
                    for (String p : c.phones) {
                        if (isSuspiciousPhone(p) || !matchesCountryFilter(p, countryFilterPos)) {
                            isSusp = true;
                            break;
                        }
                    }

                    if (isSusp) {
                        suspiciousList.add(c);
                        if (excludeSuspicious) {
                            continue; // Skip saving suspicious numbers
                        }
                    }

                    boolean dup = false;
                    for (String p : c.phones) {
                        if (existing.contains(normalizePhone(p))) {
                            dup = true;
                            break;
                        }
                    }
                    if (dup) dupList.add(c); else toSave.add(c);
                }

                final int total = fileContacts.size();
                final int dupCount = dupList.size();
                final int suspCount = suspiciousList.size();
                final int uniqueCount = toSave.size();
                analyzedToSave = toSave;
                allParsedContacts = fileContacts;
                existingPhonesForPreview = existing;
                lastAnalyzeSrcLabel = srcLabel;

                final boolean isCsvFile = !isVcf && rawCsvRows != null && !rawCsvRows.isEmpty();

                mainHandler.post(() -> {
                    progressAnalyze.setVisibility(View.GONE);
                    btnAnalyze.setEnabled(true);
                    btnProcess.setEnabled(uniqueCount > 0);
                    btnPreviewImport.setEnabled(total > 0);
                    tvAnalyzeStatus.setText("✅ Analisis selesai!");
                    layoutAnalyzeResult.setVisibility(View.VISIBLE);

                    if (isCsvFile) {
                        int numCols = 0;
                        for (List<String> r : rawCsvRows) {
                            if (r.size() > numCols) numCols = r.size();
                        }
                        if (numCols == 0) numCols = 1;

                        String[] colOptions = new String[numCols];
                        List<String> fRow = rawCsvRows.get(0);
                        List<String> sRow = rawCsvRows.size() > 1 ? rawCsvRows.get(1) : null;
                        for (int j = 0; j < numCols; j++) {
                            String header = (detectedHasHeader && j < fRow.size()) ? fRow.get(j).trim() : "";
                            String sample = (sRow != null && j < sRow.size()) ? sRow.get(j).trim() : (j < fRow.size() ? fRow.get(j).trim() : "");
                            if (sample.length() > 16) sample = sample.substring(0, 14) + "..";
                            colOptions[j] = "Kolom " + (j + 1) + (header.isEmpty() ? "" : ": " + header) + (sample.isEmpty() ? "" : " (" + sample + ")");
                        }

                        ArrayAdapter<String> colAdapter = new ArrayAdapter<String>(MainActivity.this,
                                android.R.layout.simple_spinner_item, colOptions) {
                            @Override public View getView(int pos, View cv, android.view.ViewGroup p) {
                                View v = super.getView(pos, cv, p);
                                ((TextView)v).setTextColor(0xFFE2E8F0); ((TextView)v).setTextSize(12); return v;
                            }
                            @Override public View getDropDownView(int pos, View cv, android.view.ViewGroup p) {
                                View v = super.getDropDownView(pos, cv, p);
                                ((TextView)v).setTextColor(0xFFE2E8F0); ((TextView)v).setBackgroundColor(0xFF1E293B); return v;
                            }
                        };
                        colAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

                        isApplyingMapping = true;
                        spinnerCsvNameCol.setAdapter(colAdapter);
                        spinnerCsvPhoneCol.setAdapter(colAdapter);

                        int selN = (detectedNameCol >= 0 && detectedNameCol < numCols) ? detectedNameCol : 0;
                        int selP = (detectedPhoneCol >= 0 && detectedPhoneCol < numCols) ? detectedPhoneCol : (numCols > 1 ? 1 : 0);
                        spinnerCsvNameCol.setSelection(selN);
                        spinnerCsvPhoneCol.setSelection(selP);
                        chkCsvHasHeader.setChecked(detectedHasHeader);
                        isApplyingMapping = false;

                        layoutCsvColumnMapping.setVisibility(View.VISIBLE);
                        btnViewSampleCsv.setVisibility(View.VISIBLE);
                    } else {
                        layoutCsvColumnMapping.setVisibility(View.GONE);
                        btnViewSampleCsv.setVisibility(View.GONE);
                    }

                    tvAnalyzeResult.setText(
                        "📊 HASIL ANALISIS FILE\n\n" +
                        "📁 Nama File              : " + getFileName(selectedFileUri) + "\n" +
                        "📝 Format Terdeteksi       : " + detectedFormat + "\n" +
                        "📁 Total kontak di file   : " + total + " kontak\n" +
                        "🔍 Dicek duplikat dari    : " + srcLabel + "\n" +
                        (suspCount > 0 ? "⚠️ Nomor mencurigakan/aneh : " + suspCount + " kontak (" + (excludeSuspicious ? "Akan di-skip" : "Tetap disimpan") + ")\n" : "") +
                        "🔁 Sudah ada (skip)       : " + dupCount + " kontak\n" +
                        "✨ Baru & unik             : " + uniqueCount + " kontak\n\n" +
                        (uniqueCount > 0
                            ? "➡️ Langkah 3: Pilih tujuan simpan\n   lalu tap 'Simpan Kontak Unik'."
                            : "ℹ️ Semua kontak valid di file sudah ada di HP.")
                    );
                    if (uniqueCount == 0) tvStatus.setText("ℹ️ Tidak ada kontak baru valid untuk disimpan.");
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

        // Tentukan tujuan simpan
        final String accountType;
        final String accountName;
        final String destLabel;
        if (rbSaveGoogle.isChecked()) {
            if (googleAccounts.isEmpty()) {
                Toast.makeText(this, "Tidak ada akun Google!", Toast.LENGTH_SHORT).show(); return;
            }
            int sel = spinnerGoogleAccount.getSelectedItemPosition();
            android.accounts.Account acc = googleAccounts.get(sel < 0 ? 0 : sel);
            accountType = acc.type;       // "com.google"
            accountName = acc.name;       // email
            destLabel   = "Google (" + acc.name + ")";
        } else {
            accountType = null;
            accountName = null;
            destLabel   = "Memori HP";
        }

        btnProcess.setEnabled(false);
        btnAnalyze.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        tvStatus.setText("⏳ Menyimpan " + analyzedToSave.size() + " kontak ke " + destLabel + "...");
        layoutResult.setVisibility(View.GONE);

        final List<ContactEntry> toSave = new ArrayList<>(analyzedToSave);
        executor.execute(() -> {
            try {
                int saved = saveContactsBatch(toSave, accountType, accountName);
                mainHandler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnProcess.setEnabled(false);
                    btnAnalyze.setEnabled(true);
                    analyzedToSave = null;
                    tvStatus.setText("✅ Selesai!");
                    layoutResult.setVisibility(View.VISIBLE);
                    tvResult.setText(
                        "📊 HASIL SIMPAN\n\n" +
                        "📂 Disimpan ke         : " + destLabel + "\n" +
                        "✨ Kontak unik          : " + toSave.size() + " kontak\n" +
                        "💾 Berhasil disimpan   : " + saved + " kontak\n" +
                        (saved < toSave.size() ? "⚠️ Gagal simpan       : " + (toSave.size() - saved) + " kontak" : "")
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
        tvStatsTotal.setText("📱 Total Kontak: -");
        tvStatsUnique.setText("✨ Unik: -");
        tvStatsDuplicate.setText("🔁 Duplikat: -");
        tvStatsSource.setText("📂 Sumber Kontak:\n💡 Pilih sumber filter di atas, lalu tap 'Terapkan Filter & Hitung Ulang'.");
        tvStatsStatus.setText("Tap 'Terapkan Filter & Hitung Ulang' untuk memulai.");
        progressStats.setVisibility(View.GONE);
        btnDeleteDuplicates.setEnabled(false);
        btnDeleteAll.setEnabled(false);
        btnBackupFilterAll.setEnabled(false);
        btnBackupFilterUnique.setEnabled(false);
        btnViewContacts.setEnabled(false);
        if (layoutCheckPhoneResult != null) layoutCheckPhoneResult.setVisibility(View.GONE);
        if (progressCheckPhone != null) progressCheckPhone.setVisibility(View.GONE);
    }

    private void checkSinglePhoneNumber() {
        if (etCheckPhoneNumber == null) return;
        String input = etCheckPhoneNumber.getText().toString().trim();
        if (input.isEmpty()) {
            Toast.makeText(this, "Masukkan nomor telepon yang ingin dicek!", Toast.LENGTH_SHORT).show();
            return;
        }

        String inputNorm = normalizePhone(input);
        if (inputNorm.isEmpty()) {
            Toast.makeText(this, "Format nomor tidak valid!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Hide keyboard
        try {
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null && getCurrentFocus() != null) {
                imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
            }
        } catch (Exception ignored) {}

        btnCheckSinglePhone.setEnabled(false);
        progressCheckPhone.setVisibility(View.VISIBLE);
        layoutCheckPhoneResult.setVisibility(View.GONE);

        executor.execute(() -> {
            try {
                List<PhoneContact> allContacts = getAllContactsWithSource();
                List<PhoneContact> matches = new ArrayList<>();

                for (PhoneContact c : allContacts) {
                    if (c.phone != null && !c.phone.trim().isEmpty()) {
                        String contactNorm = normalizePhone(c.phone);
                        if (contactNorm.equals(inputNorm)) {
                            matches.add(c);
                        }
                    }
                }

                final int count = matches.size();
                final List<PhoneContact> finalMatches = matches;
                final String searchedNumber = input;

                mainHandler.post(() -> {
                    progressCheckPhone.setVisibility(View.GONE);
                    btnCheckSinglePhone.setEnabled(true);
                    layoutCheckPhoneResult.setVisibility(View.VISIBLE);

                    if (count > 0) {
                        tvCheckPhoneResultHeader.setText("✅ DITEMUKAN: " + count + " Kontak di HP");
                        tvCheckPhoneResultHeader.setTextColor(0xFF34D399); // Green

                        StringBuilder sb = new StringBuilder();
                        sb.append("Nomor yang dicek: ").append(searchedNumber).append("\n\n");

                        for (int i = 0; i < finalMatches.size(); i++) {
                            PhoneContact c = finalMatches.get(i);
                            String src = resolveSource(c.accountType);
                            String sourceLabel;
                            if (src.equals(SRC_GOOGLE) && c.accountName != null) {
                                sourceLabel = "☁️ Google (" + c.accountName + ")";
                            } else if (src.equals(SRC_PHONE)) {
                                sourceLabel = "📱 Memori HP (Lokal)";
                            } else if (src.equals(SRC_SIM)) {
                                sourceLabel = "💳 Kartu SIM";
                            } else if (src.equals(SRC_WA_BIZ)) {
                                sourceLabel = "💬 WhatsApp Business";
                            } else if (src.equals(SRC_WA)) {
                                sourceLabel = "💬 WhatsApp";
                            } else {
                                sourceLabel = "📁 " + src;
                            }

                            sb.append(i + 1).append(". ")
                              .append(c.name != null && !c.name.isEmpty() ? c.name : "(Tanpa Nama)")
                              .append("\n   📞 ").append(c.phone != null ? c.phone : "-")
                              .append("\n   📍 Sumber: ").append(sourceLabel)
                              .append("\n\n");
                        }

                        tvCheckPhoneResultDetails.setText(sb.toString().trim());
                    } else {
                        tvCheckPhoneResultHeader.setText("❌ BELUM TERSIMPAN DI HP");
                        tvCheckPhoneResultHeader.setTextColor(0xFFEF4444); // Red

                        tvCheckPhoneResultDetails.setText(
                            "Nomor: " + searchedNumber + "\n\n" +
                            "ℹ️ Nomor ini tidak ditemukan di Google Account, Memori HP, Kartu SIM, maupun WhatsApp."
                        );
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    progressCheckPhone.setVisibility(View.GONE);
                    btnCheckSinglePhone.setEnabled(true);
                    layoutCheckPhoneResult.setVisibility(View.VISIBLE);
                    tvCheckPhoneResultHeader.setText("❌ Terjadi Kesalahan");
                    tvCheckPhoneResultHeader.setTextColor(0xFFEF4444);
                    tvCheckPhoneResultDetails.setText("Error: " + e.getMessage());
                });
            }
        });
    }

    private void calculateStats() {
        tvStatsTotal.setText("⏳ Menghitung...");
        tvStatsDuplicate.setText("⏳");
        tvStatsUnique.setText("⏳");
        tvStatsSource.setText("⏳ Memuat sumber...");
        tvStatsStatus.setText("");
        btnDeleteDuplicates.setEnabled(false);
        btnDeleteAll.setEnabled(false);
        btnBackupFilterAll.setEnabled(false);
        btnBackupFilterUnique.setEnabled(false);
        btnViewContacts.setEnabled(false);
        progressStats.setVisibility(View.VISIBLE);

        // Baca filter checkbox
        final boolean inclGoogle  = chkGoogle.isChecked();
        final boolean inclPhone   = chkPhone.isChecked();
        final boolean inclSim     = chkSim.isChecked();
        final boolean inclWa      = chkWa.isChecked();
        final boolean inclWaBiz   = chkWaBusiness.isChecked();
        final boolean inclOther   = chkOther.isChecked();

        final int selStatsGoogle = (spinnerStatsGoogleAccount != null && spinnerStatsGoogleAccount.getSelectedItemPosition() > 0)
            ? spinnerStatsGoogleAccount.getSelectedItemPosition() : 0;
        final String selectedStatsGoogleAccountName = (selStatsGoogle > 0 && selStatsGoogle - 1 < googleAccountNames.size())
            ? googleAccountNames.get(selStatsGoogle - 1) : null;

        executor.execute(() -> {
            try {
                List<PhoneContact> all = getAllContactsWithSource();

                // Apply filter
                List<PhoneContact> filtered = new ArrayList<>();
                for (PhoneContact c : all) {
                    String src = resolveSource(c.accountType);
                    if (src.equals(SRC_GOOGLE)) {
                        if (!inclGoogle) continue;
                        if (selectedStatsGoogleAccountName != null && (c.accountName == null || !c.accountName.equalsIgnoreCase(selectedStatsGoogleAccountName))) continue;
                    }
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

                // Build source map with detailed per-account breakdown
                Map<String, Integer> srcMap = new LinkedHashMap<>();
                Map<String, Integer> googleAccountDetails = new LinkedHashMap<>();
                for (PhoneContact c : filtered) {
                    String src = resolveSource(c.accountType);
                    srcMap.put(src, srcMap.getOrDefault(src, 0) + 1);
                    if (src.equals(SRC_GOOGLE) && c.accountName != null && !c.accountName.isEmpty()) {
                        googleAccountDetails.put(c.accountName, googleAccountDetails.getOrDefault(c.accountName, 0) + 1);
                    }
                }
                StringBuilder sb = new StringBuilder();
                for (Map.Entry<String, Integer> e : srcMap.entrySet()) {
                    if (e.getKey().equals(SRC_GOOGLE)) {
                        if (selectedStatsGoogleAccountName != null) {
                            sb.append("• ").append(e.getKey()).append(" (").append(selectedStatsGoogleAccountName).append("): ").append(e.getValue()).append(" kontak\n");
                        } else {
                            sb.append("• ").append(e.getKey()).append(" (Semua Akun): ").append(e.getValue()).append(" kontak\n");
                            for (Map.Entry<String, Integer> gEntry : googleAccountDetails.entrySet()) {
                                sb.append("   └ 📧 ").append(gEntry.getKey()).append(": ").append(gEntry.getValue()).append(" kontak\n");
                            }
                        }
                    } else {
                        sb.append("• ").append(e.getKey()).append(": ").append(e.getValue()).append(" kontak\n");
                    }
                }

                final int fTotal = total, fDup = dupCount, fUniq = uniqueCount;
                final String fSrc = sb.toString().trim();
                final List<PhoneContact> fFiltered = filtered;

                mainHandler.post(() -> {
                    lastFilteredContacts = fFiltered;
                    progressStats.setVisibility(View.GONE);
                    tvStatsTotal.setText("📱 Total Kontak (filter): " + fTotal);
                    tvStatsUnique.setText("✨ Unik: " + fUniq + " kontak");
                    tvStatsDuplicate.setText("🔁 Duplikat: " + fDup + " kontak");
                    tvStatsSource.setText("📂 Sumber Kontak:\n" + fSrc);
                    btnDeleteDuplicates.setEnabled(fDup > 0);
                    btnDeleteAll.setEnabled(fTotal > 0);
                    btnBackupFilterAll.setEnabled(fTotal > 0);
                    btnBackupFilterUnique.setEnabled(fTotal > 0);
                    btnViewContacts.setEnabled(fTotal > 0);
                    if (fDup == 0) tvStatsStatus.setText("✅ Tidak ada duplikat!");
                    else tvStatsStatus.setText("Siap.");
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    progressStats.setVisibility(View.GONE);
                    tvStatsStatus.setText("❌ Error: " + e.getMessage());
                });
            }
        });
    }

    // ─── LIHAT KONTAK DIALOG ─────────────────────────────────────────────────────

    private void showContactListDialog() {
        if (lastFilteredContacts.isEmpty()) {
            Toast.makeText(this, "Tidak ada kontak untuk ditampilkan.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Build sets of IDs: will-be-deleted (merah) and kept-but-duplicate (orange)
        Set<Long> toDeleteIds = new HashSet<>();
        Set<Long> keptDupIds  = new HashSet<>();

        for (DuplicateGroup g : duplicateGroups) {
            List<PhoneContact> sorted = new ArrayList<>(g.contacts);
            sorted.sort((a, b) -> {
                int ia = priorityOrder.indexOf(resolveSource(a.accountType));
                int ib = priorityOrder.indexOf(resolveSource(b.accountType));
                if (ia < 0) ia = priorityOrder.size();
                if (ib < 0) ib = priorityOrder.size();
                return Integer.compare(ia, ib);
            });
            keptDupIds.add(sorted.get(0).rawContactId);
            for (int i = 1; i < sorted.size(); i++) toDeleteIds.add(sorted.get(i).rawContactId);
        }

        // Sort: merah (hapus) dulu → orange (dup dipertahankan) → hijau (unik), lalu nama
        List<PhoneContact> sorted = new ArrayList<>(lastFilteredContacts);
        sorted.sort((a, b) -> {
            int ra = toDeleteIds.contains(a.rawContactId) ? 0 : keptDupIds.contains(a.rawContactId) ? 1 : 2;
            int rb = toDeleteIds.contains(b.rawContactId) ? 0 : keptDupIds.contains(b.rawContactId) ? 1 : 2;
            if (ra != rb) return Integer.compare(ra, rb);
            String na = a.name == null ? "" : a.name;
            String nb = b.name == null ? "" : b.name;
            return na.compareToIgnoreCase(nb);
        });

        // Count for title
        int cRed = 0, cOrange = 0, cGreen = 0;
        for (PhoneContact c : sorted) {
            if (toDeleteIds.contains(c.rawContactId)) cRed++;
            else if (keptDupIds.contains(c.rawContactId)) cOrange++;
            else cGreen++;
        }

        float dp = getResources().getDisplayMetrics().density;
        int pad = (int)(12 * dp);

        // Legend header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackgroundColor(0xFF0F172A);
        header.setPadding(pad, pad, pad, pad);

        LinearLayout legendRow = new LinearLayout(this);
        legendRow.setOrientation(LinearLayout.HORIZONTAL);
        addLegendItem(legendRow, "🔴 Hapus  ", 0xFFEF4444);
        addLegendItem(legendRow, "🟠 Dup dipertahankan  ", 0xFFF97316);
        addLegendItem(legendRow, "🟢 Unik", 0xFF34D399);
        header.addView(legendRow);

        TextView tvCount = new TextView(this);
        tvCount.setText("Total: " + sorted.size() + " kontak  |  🔴" + cRed + "  🟠" + cOrange + "  🟢" + cGreen);
        tvCount.setTextColor(0xFF64748B);
        tvCount.setTextSize(11);
        tvCount.setPadding(0, (int)(6*dp), 0, 0);
        header.addView(tvCount);

        // ListView with ArrayAdapter — recycles views automatically, handles 100k+ contacts
        android.widget.ListView listView = new android.widget.ListView(this);
        listView.setBackgroundColor(0xFF0F172A);
        listView.setDivider(null);
        listView.setDividerHeight((int)(4 * dp));

        final Set<Long> finalToDelete = toDeleteIds;
        final Set<Long> finalKeptDup  = keptDupIds;

        android.widget.ArrayAdapter<PhoneContact> adapter = new android.widget.ArrayAdapter<PhoneContact>(
                this, 0, sorted) {
            @Override
            public android.view.View getView(int position, android.view.View convertView, android.view.ViewGroup parent) {
                // ViewHolder pattern for smooth scrolling
                ViewHolder vh;
                if (convertView == null) {
                    LinearLayout rowOuter = new LinearLayout(getContext());
                    rowOuter.setOrientation(LinearLayout.HORIZONTAL);

                    android.view.View bar = new android.view.View(getContext());
                    LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams((int)(4*dp), LinearLayout.LayoutParams.MATCH_PARENT);
                    bar.setLayoutParams(barLp);

                    LinearLayout inner = new LinearLayout(getContext());
                    inner.setOrientation(LinearLayout.VERTICAL);
                    inner.setBackgroundColor(0xFF1E293B);
                    inner.setPadding((int)(10*dp), (int)(8*dp), (int)(10*dp), (int)(8*dp));
                    inner.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

                    TextView tvName  = new TextView(getContext());
                    tvName.setTextSize(13); tvName.setTextColor(0xFFE2E8F0);
                    tvName.setTypeface(null, android.graphics.Typeface.BOLD);

                    TextView tvPhone = new TextView(getContext());
                    tvPhone.setTextSize(11); tvPhone.setTextColor(0xFF94A3B8);

                    TextView tvSrc   = new TextView(getContext());
                    tvSrc.setTextSize(10);

                    inner.addView(tvName);
                    inner.addView(tvPhone);
                    inner.addView(tvSrc);

                    rowOuter.addView(bar);
                    rowOuter.addView(inner);
                    convertView = rowOuter;

                    vh = new ViewHolder();
                    vh.bar = bar; vh.tvName = tvName; vh.tvPhone = tvPhone; vh.tvSrc = tvSrc;
                    convertView.setTag(vh);
                } else {
                    vh = (ViewHolder) convertView.getTag();
                }

                PhoneContact c = getItem(position);
                int color;
                String label;
                if (finalToDelete.contains(c.rawContactId)) {
                    color = 0xFFEF4444; label = "🔴 HAPUS";
                } else if (finalKeptDup.contains(c.rawContactId)) {
                    color = 0xFFF97316; label = "🟠 DUPLIKAT";
                } else {
                    color = 0xFF34D399; label = "🟢 UNIK";
                }

                vh.bar.setBackgroundColor(color);
                vh.tvName.setText(c.name != null && !c.name.isEmpty() ? c.name : "(Tanpa Nama)");
                vh.tvPhone.setText("📞 " + (c.phone != null ? c.phone : "-"));

                String src = resolveSource(c.accountType);
                String srcDisplay = src;
                if (src.equals(SRC_GOOGLE) && c.accountName != null && !c.accountName.isEmpty())
                    srcDisplay = src + " (" + c.accountName + ")";
                vh.tvSrc.setText("📂 " + srcDisplay + "   " + label);
                vh.tvSrc.setTextColor(color);

                return convertView;
            }
        };

        listView.setAdapter(adapter);

        // Wrap header + listview in a LinearLayout
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.addView(header);
        root.addView(listView);

        // Make dialog fullscreen-ish
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("📋 Daftar Kontak (" + sorted.size() + ")")
            .setView(root)
            .setPositiveButton("Tutup", null)
            .create();

        dialog.setOnShowListener(d -> {
            // Give ListView most of screen height
            int screenH = getResources().getDisplayMetrics().heightPixels;
            listView.setMinimumHeight((int)(screenH * 0.65f));
        });
        dialog.show();
    }

    static class ViewHolder {
        android.view.View bar;
        TextView tvName, tvPhone, tvSrc;
    }

    private void addLegendItem(LinearLayout parent, String text, int color) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTextSize(10);
        parent.addView(tv);
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
        tvStatsStatus.setText("⏳ Menyiapkan daftar hapus...");

        executor.execute(() -> {
            int deleted = 0;
            try {
                // Kumpulkan semua ID yang akan dihapus dulu
                List<Long> toDeleteIds = new ArrayList<>();
                for (DuplicateGroup g : duplicateGroups) {
                    List<PhoneContact> sorted = new ArrayList<>(g.contacts);
                    sorted.sort((a, b) -> {
                        int ia = priorityOrder.indexOf(resolveSource(a.accountType));
                        int ib = priorityOrder.indexOf(resolveSource(b.accountType));
                        if (ia < 0) ia = priorityOrder.size();
                        if (ib < 0) ib = priorityOrder.size();
                        return Integer.compare(ia, ib);
                    });
                    // index 0 = dipertahankan, sisanya dihapus
                    for (int i = 1; i < sorted.size(); i++)
                        toDeleteIds.add(sorted.get(i).rawContactId);
                }

                final int total = toDeleteIds.size();
                final Uri deleteUri = ContactsContract.RawContacts.CONTENT_URI.buildUpon()
                    .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build();

                // Batch per 500 — aman dari TransactionTooLargeException
                final int CHUNK = 500;
                int processed = 0;
                while (processed < toDeleteIds.size()) {
                    int end = Math.min(processed + CHUNK, toDeleteIds.size());
                    List<Long> chunk = toDeleteIds.subList(processed, end);

                    ArrayList<ContentProviderOperation> ops = new ArrayList<>();
                    for (Long id : chunk) {
                        ops.add(ContentProviderOperation.newDelete(deleteUri)
                            .withSelection(ContactsContract.RawContacts._ID + "=?",
                                new String[]{String.valueOf(id)})
                            .build());
                    }

                    try {
                        android.content.ContentProviderResult[] results =
                            getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
                        for (android.content.ContentProviderResult r : results)
                            if (r.count != null && r.count > 0) deleted++;
                    } catch (Exception chunkEx) {
                        // Kalau satu chunk gagal, lanjut chunk berikutnya
                    }

                    processed = end;
                    final int prog = processed, tot = total, del = deleted;
                    mainHandler.post(() ->
                        tvStatsStatus.setText("⏳ Menghapus... " + prog + "/" + tot + " (" + del + " berhasil)")
                    );
                }

                final int fd = deleted;
                mainHandler.post(() -> {
                    progressStats.setVisibility(View.GONE);
                    tvStatsStatus.setText("✅ Berhasil hapus " + fd + " duplikat!");
                    duplicateGroups.clear();
                    calculateStats();
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

    // ─── HAPUS SEMUA ─────────────────────────────────────────────────────────────

    private void confirmDeleteAll() {
        int count = lastFilteredContacts.size();
        if (count == 0) {
            Toast.makeText(this, "Tidak ada kontak yang dipilih filter.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Build sumber yang aktif
        List<String> activeFilters = new ArrayList<>();
        if (chkGoogle.isChecked()) {
            int sel = (spinnerStatsGoogleAccount != null) ? spinnerStatsGoogleAccount.getSelectedItemPosition() : 0;
            if (sel > 0 && sel - 1 < googleAccountNames.size()) {
                activeFilters.add("Google Account (" + googleAccountNames.get(sel - 1) + ")");
            } else {
                activeFilters.add("Google Account (Semua Akun)");
            }
        }
        if (chkPhone.isChecked())       activeFilters.add("Memori HP");
        if (chkSim.isChecked())         activeFilters.add("SIM Card");
        if (chkWa.isChecked())          activeFilters.add("WhatsApp");
        if (chkWaBusiness.isChecked())  activeFilters.add("WA Bisnis");
        if (chkOther.isChecked())       activeFilters.add("Sumber Lain");

        String srcLabel = activeFilters.isEmpty() ? "semua sumber" : String.join(", ", activeFilters);

        new AlertDialog.Builder(this)
            .setTitle("🚨 HAPUS SEMUA Kontak")
            .setMessage("Akan menghapus SEMUA " + count + " kontak dari:\n" + srcLabel +
                "\n\n⚠️ Kontak dari Google Account akan ikut terhapus di server Google." +
                "\n\n❗ Aksi ini TIDAK BISA dibatalkan!\n\nLanjutkan?")
            .setPositiveButton("Ya, Hapus Semua", (d, w) -> deleteAllFiltered())
            .setNegativeButton("Batal", null)
            .show();
    }

    private void deleteAllFiltered() {
        if (lastFilteredContacts.isEmpty()) return;

        btnDeleteAll.setEnabled(false);
        btnDeleteDuplicates.setEnabled(false);
        progressStats.setVisibility(View.VISIBLE);
        tvStatsStatus.setText("⏳ Menyiapkan daftar hapus...");

        final List<Long> toDeleteIds = new ArrayList<>();
        for (PhoneContact c : lastFilteredContacts) toDeleteIds.add(c.rawContactId);

        executor.execute(() -> {
            int deleted = 0;
            final int total = toDeleteIds.size();
            final Uri deleteUri = ContactsContract.RawContacts.CONTENT_URI.buildUpon()
                .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build();

            // Batch 500 per chunk — optimal balance antara speed & TransactionTooLarge
            final int CHUNK = 500;
            int processed = 0;

            while (processed < toDeleteIds.size()) {
                int end = Math.min(processed + CHUNK, toDeleteIds.size());
                List<Long> chunk = toDeleteIds.subList(processed, end);

                ArrayList<ContentProviderOperation> ops = new ArrayList<>();
                for (Long id : chunk) {
                    ops.add(ContentProviderOperation.newDelete(deleteUri)
                        .withSelection(ContactsContract.RawContacts._ID + "=?",
                            new String[]{String.valueOf(id)})
                        .build());
                }
                try {
                    android.content.ContentProviderResult[] results =
                        getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
                    for (android.content.ContentProviderResult r : results)
                        if (r.count != null && r.count > 0) deleted++;
                } catch (Exception chunkEx) {
                    // Lanjut chunk berikutnya jika ada error
                }

                processed = end;
                final int prog = processed, del = deleted;
                mainHandler.post(() ->
                    tvStatsStatus.setText("⏳ Menghapus semua... " + prog + "/" + total + " (" + del + " berhasil)")
                );
            }

            final int fd = deleted;
            mainHandler.post(() -> {
                progressStats.setVisibility(View.GONE);
                tvStatsStatus.setText("✅ Berhasil hapus " + fd + " kontak!");
                lastFilteredContacts.clear();
                duplicateGroups.clear();
                calculateStats(); // refresh stats
            });
        });
    }

    // ─── BACKUP FILTER AKTIF ─────────────────────────────────────────────────────

    private void startBackupFiltered(boolean uniqueOnly) {
        if (lastFilteredContacts.isEmpty()) {
            Toast.makeText(this, "Tidak ada kontak. Terapkan filter dulu.", Toast.LENGTH_SHORT).show();
            return;
        }

        btnBackupFilterAll.setEnabled(false);
        btnBackupFilterUnique.setEnabled(false);
        progressStats.setVisibility(View.VISIBLE);
        tvStatsStatus.setText("⏳ Menyiapkan backup...");

        executor.execute(() -> {
            try {
                List<PhoneContact> toBackup;
                if (uniqueOnly) {
                    // Ambil hanya yang nomor unik (pertama muncul)
                    Map<String, Boolean> seen = new LinkedHashMap<>();
                    toBackup = new ArrayList<>();
                    for (PhoneContact c : lastFilteredContacts) {
                        String norm = normalizePhone(c.phone);
                        if (!seen.containsKey(norm)) {
                            seen.put(norm, true);
                            toBackup.add(c);
                        }
                    }
                } else {
                    toBackup = new ArrayList<>(lastFilteredContacts);
                }

                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                String label = uniqueOnly ? "filter_unik" : "filter_all";

                File dir = new File(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS), "ContactSaver");
                if (!dir.exists()) dir.mkdirs();

                File csvFile = new File(dir, "backup_" + label + "_" + timestamp + ".csv");
                File vcfFile = new File(dir, "backup_" + label + "_" + timestamp + ".vcf");

                final int total = toBackup.size();
                mainHandler.post(() -> tvStatsStatus.setText("⏳ Menulis CSV... (0/" + total + ")"));

                BufferedWriter csvWriter = new BufferedWriter(new FileWriter(csvFile));
                csvWriter.write("nama,nomor,sumber\n");
                int i = 0;
                for (PhoneContact c : toBackup) {
                    String src = resolveSource(c.accountType);
                    csvWriter.write("\"" + safe(c.name) + "\",\"" + safe(c.phone) + "\",\"" + src + "\"\n");
                    i++;
                    if (i % 500 == 0) {
                        final int prog = i;
                        mainHandler.post(() -> tvStatsStatus.setText("⏳ Menulis CSV... (" + prog + "/" + total + ")"));
                    }
                }
                csvWriter.close();

                mainHandler.post(() -> tvStatsStatus.setText("⏳ Menulis VCF... (0/" + total + ")"));

                BufferedWriter vcfWriter = new BufferedWriter(new FileWriter(vcfFile));
                i = 0;
                for (PhoneContact c : toBackup) {
                    vcfWriter.write("BEGIN:VCARD\n");
                    vcfWriter.write("VERSION:3.0\n");
                    vcfWriter.write("FN:" + safe(c.name) + "\n");
                    vcfWriter.write("TEL;TYPE=CELL:" + safe(c.phone) + "\n");
                    vcfWriter.write("END:VCARD\n\n");
                    i++;
                    if (i % 500 == 0) {
                        final int prog = i;
                        mainHandler.post(() -> tvStatsStatus.setText("⏳ Menulis VCF... (" + prog + "/" + total + ")"));
                    }
                }
                vcfWriter.close();

                final String csvPath = csvFile.getAbsolutePath();
                final String vcfPath = vcfFile.getAbsolutePath();
                final int count = toBackup.size();

                mainHandler.post(() -> {
                    progressStats.setVisibility(View.GONE);
                    btnBackupFilterAll.setEnabled(true);
                    btnBackupFilterUnique.setEnabled(true);
                    tvStatsStatus.setText(
                        "✅ Backup selesai! " + count + " kontak\n" +
                        "📄 CSV: " + csvPath + "\n" +
                        "📋 VCF: " + vcfPath
                    );
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    progressStats.setVisibility(View.GONE);
                    btnBackupFilterAll.setEnabled(true);
                    btnBackupFilterUnique.setEnabled(true);
                    tvStatsStatus.setText("❌ Error: " + e.getMessage());
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
                ContactsContract.RawContacts.ACCOUNT_TYPE,
                ContactsContract.RawContacts.ACCOUNT_NAME
            }, null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC");
        if (c != null) {
            while (c.moveToNext()) {
                PhoneContact p = new PhoneContact();
                p.name = c.getString(0); p.phone = c.getString(1);
                p.rawContactId = c.getLong(2); p.accountType = c.getString(3);
                p.accountName = c.getString(4);
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
        rawCsvRows.clear();
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
        List<List<String>> allRows = new ArrayList<>();
        String line;
        while ((line = r.readLine()) != null) {
            allRows.add(parseCsvLine(line));
        }
        r.close();

        if (allRows.isEmpty()) {
            detectedFormat = "File CSV Kosong";
            return list;
        }

        List<String> firstRow = allRows.get(0);
        int numCols = 0;
        for (List<String> row : allRows) {
            if (row.size() > numCols) numCols = row.size();
        }

        // 1. Check if first row is a header
        int headerMatches = 0;
        for (String col : firstRow) {
            if (isPhoneHeader(col) || isNameHeader(col)) {
                headerMatches++;
            }
        }
        boolean hasHeader = headerMatches >= 1;

        List<Integer> phoneIndices = new ArrayList<>();
        List<Integer> nameIndices = new ArrayList<>();

        if (hasHeader) {
            for (int i = 0; i < firstRow.size(); i++) {
                String col = firstRow.get(i);
                if (isPhoneHeader(col)) {
                    phoneIndices.add(i);
                } else if (isNameHeader(col)) {
                    nameIndices.add(i);
                }
            }
        }

        // If no headers found or missing one of them, fall back/verify with auto-detect
        boolean fellBackToAutoDetect = false;
        int autoPi = -1, autoNi = -1;
        if (phoneIndices.isEmpty() || nameIndices.isEmpty()) {
            int bestPi = -1;
            int maxPhoneScore = -1;
            for (int j = 0; j < numCols; j++) {
                int score = calculateColumnPhoneScore(allRows, j);
                if (score > maxPhoneScore) {
                    maxPhoneScore = score;
                    bestPi = j;
                }
            }

            int bestNi = -1;
            int maxNameScore = -1;
            for (int j = 0; j < numCols; j++) {
                if (j == bestPi) continue;
                int score = calculateColumnNameScore(allRows, j, bestPi);
                if (score > maxNameScore) {
                    maxNameScore = score;
                    bestNi = j;
                }
            }

            // Fallback defaults
            if (bestPi == -1 || maxPhoneScore == 0) bestPi = (numCols > 1 ? 1 : 0);
            if (bestNi == -1 || maxNameScore == 0) bestNi = (bestPi == 0 && numCols > 1) ? 1 : 0;

            autoPi = bestPi;
            autoNi = bestNi;

            if (phoneIndices.isEmpty()) phoneIndices.add(bestPi);
            if (nameIndices.isEmpty()) nameIndices.add(bestNi);
            fellBackToAutoDetect = true;
        }

        // Check if it's Google Contacts
        boolean isGoogleContacts = false;
        if (hasHeader) {
            for (String col : firstRow) {
                String c = col.toLowerCase().replaceAll("[^a-z0-9]", "");
                if (c.equals("phone1value") || c.equals("firstname") || c.equals("lastname")) {
                    isGoogleContacts = true;
                    break;
                }
            }
        }

        // Set detected format info
        if (isGoogleContacts) {
            detectedFormat = "Google Contacts CSV (Multi-kolom)";
        } else if (hasHeader && !fellBackToAutoDetect) {
            StringBuilder phoneCols = new StringBuilder();
            for (int idx : phoneIndices) {
                if (phoneCols.length() > 0) phoneCols.append(", ");
                if (idx < firstRow.size()) phoneCols.append("'").append(firstRow.get(idx)).append("'");
            }
            StringBuilder nameCols = new StringBuilder();
            for (int idx : nameIndices) {
                if (nameCols.length() > 0) nameCols.append(", ");
                if (idx < firstRow.size()) nameCols.append("'").append(firstRow.get(idx)).append("'");
            }
            detectedFormat = "CSV dengan Header [Nama: " + nameCols.toString() + ", Nomor: " + phoneCols.toString() + "]";
        } else {
            int pi = phoneIndices.get(0);
            int ni = nameIndices.get(0);
            detectedFormat = (hasHeader ? "CSV dengan Header Kustom" : "CSV tanpa Header") + " - Auto-detect [Nama: Kolom " + (ni + 1) + ", Nomor: Kolom " + (pi + 1) + "]";
        }

        // Determine if we should skip the first row (header row)
        boolean skipFirstRow = hasHeader;
        if (!skipFirstRow && allRows.size() > 1) {
            int pi = phoneIndices.get(0);
            if (pi < firstRow.size()) {
                String firstVal = firstRow.get(pi);
                if (!isLikelyPhone(firstVal)) {
                    int phoneCountInSubsequent = 0;
                    int checkLimit = Math.min(allRows.size(), 10);
                    for (int i = 1; i < checkLimit; i++) {
                        List<String> row = allRows.get(i);
                        if (pi < row.size() && isLikelyPhone(row.get(pi))) {
                            phoneCountInSubsequent++;
                        }
                    }
                    if (phoneCountInSubsequent > 0) {
                        skipFirstRow = true;
                        // Adjust detectedFormat name since first row was indeed a header
                        detectedFormat = "CSV dengan Header (Unrecognized) - Auto-detect [Nama: Kolom " + (nameIndices.get(0) + 1) + ", Nomor: Kolom " + (pi + 1) + "]";
                    }
                }
            }
        }

        // 3. Parse all data rows
        int startRow = skipFirstRow ? 1 : 0;
        for (int i = startRow; i < allRows.size(); i++) {
            List<String> row = allRows.get(i);
            
            // Build name
            StringBuilder sbName = new StringBuilder();
            for (int idx : nameIndices) {
                if (idx < row.size()) {
                    String val = row.get(idx).trim();
                    if (!val.isEmpty()) {
                        if (sbName.length() > 0) sbName.append(" ");
                        sbName.append(val);
                    }
                }
            }
            
            // Get all phone numbers
            List<String> rowPhones = new ArrayList<>();
            for (int idx : phoneIndices) {
                if (idx < row.size()) {
                    String phone = row.get(idx).trim();
                    if (!phone.isEmpty()) {
                        rowPhones.add(phone);
                    }
                }
            }
            
            if (!rowPhones.isEmpty()) {
                ContactEntry entry = new ContactEntry();
                entry.name = sbName.toString().trim();
                if (entry.name.isEmpty()) {
                    entry.name = rowPhones.get(0);
                }
                entry.phones.addAll(rowPhones);
                list.add(entry);
            }
        }

        detectedNameCol = nameIndices.isEmpty() ? 0 : nameIndices.get(0);
        detectedPhoneCol = phoneIndices.isEmpty() ? (numCols > 1 ? 1 : 0) : phoneIndices.get(0);
        detectedHasHeader = skipFirstRow;
        rawCsvRows = allRows;

        return list;
    }

    private void showSampleCsvDialog() {
        if (rawCsvRows == null || rawCsvRows.isEmpty()) {
            Toast.makeText(this, "Tidak ada data CSV untuk ditampilkan.", Toast.LENGTH_SHORT).show();
            return;
        }

        final int totalRows = rawCsvRows.size();
        int maxCols = 0;
        int checkLimit = Math.min(totalRows, 100);
        for (int i = 0; i < checkLimit; i++) {
            if (rawCsvRows.get(i).size() > maxCols) maxCols = rawCsvRows.get(i).size();
        }
        if (maxCols == 0) maxCols = 1;
        final int finalMaxCols = maxCols;

        float dp = getResources().getDisplayMetrics().density;
        final int colWidth = (int)(150 * dp);
        final int rowIdxWidth = (int)(60 * dp);
        final int cellPadH = (int)(12 * dp);
        final int cellPadV = (int)(10 * dp);

        // Header Row
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setBackgroundColor(0xFF1E293B);

        TextView tvNoHdr = new TextView(this);
        tvNoHdr.setText("Baris");
        tvNoHdr.setTextColor(0xFF38BDF8);
        tvNoHdr.setTextSize(12);
        tvNoHdr.setTypeface(null, android.graphics.Typeface.BOLD);
        tvNoHdr.setGravity(android.view.Gravity.CENTER_VERTICAL);
        tvNoHdr.setPadding(cellPadH, cellPadV, cellPadH, cellPadV);
        tvNoHdr.setLayoutParams(new LinearLayout.LayoutParams(rowIdxWidth, LinearLayout.LayoutParams.WRAP_CONTENT));
        headerRow.addView(tvNoHdr);

        for (int c = 0; c < finalMaxCols; c++) {
            TextView tvCol = new TextView(this);
            tvCol.setText("Kolom " + (c + 1));
            tvCol.setTextColor(0xFF38BDF8);
            tvCol.setTextSize(12);
            tvCol.setTypeface(null, android.graphics.Typeface.BOLD);
            tvCol.setGravity(android.view.Gravity.CENTER_VERTICAL);
            tvCol.setPadding(cellPadH, cellPadV, cellPadH, cellPadV);
            tvCol.setLayoutParams(new LinearLayout.LayoutParams(colWidth, LinearLayout.LayoutParams.WRAP_CONTENT));
            headerRow.addView(tvCol);
        }

        // Virtualized ListView for buttery-smooth rendering of all rows
        android.widget.ListView listView = new android.widget.ListView(this);
        listView.setBackgroundColor(0xFF0F172A);
        listView.setDivider(null);

        class CsvRowViewHolder {
            TextView tvRowIdx;
            TextView[] tvCells;
        }

        android.widget.ArrayAdapter<List<String>> adapter = new android.widget.ArrayAdapter<List<String>>(
                this, 0, rawCsvRows) {
            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                CsvRowViewHolder vh;
                if (convertView == null) {
                    LinearLayout row = new LinearLayout(getContext());
                    row.setOrientation(LinearLayout.HORIZONTAL);

                    TextView tvIdx = new TextView(getContext());
                    tvIdx.setTextSize(11);
                    tvIdx.setTextColor(0xFF94A3B8);
                    tvIdx.setGravity(android.view.Gravity.CENTER_VERTICAL);
                    tvIdx.setPadding(cellPadH, cellPadV, cellPadH, cellPadV);
                    tvIdx.setLayoutParams(new LinearLayout.LayoutParams(rowIdxWidth, LinearLayout.LayoutParams.WRAP_CONTENT));
                    row.addView(tvIdx);

                    TextView[] cells = new TextView[finalMaxCols];
                    for (int c = 0; c < finalMaxCols; c++) {
                        TextView tvCell = new TextView(getContext());
                        tvCell.setTextSize(12);
                        tvCell.setTextColor(0xFFE2E8F0);
                        tvCell.setTypeface(android.graphics.Typeface.MONOSPACE);
                        tvCell.setGravity(android.view.Gravity.CENTER_VERTICAL);
                        tvCell.setPadding(cellPadH, cellPadV, cellPadH, cellPadV);
                        tvCell.setLayoutParams(new LinearLayout.LayoutParams(colWidth, LinearLayout.LayoutParams.WRAP_CONTENT));
                        row.addView(tvCell);
                        cells[c] = tvCell;
                    }

                    vh = new CsvRowViewHolder();
                    vh.tvRowIdx = tvIdx;
                    vh.tvCells = cells;
                    row.setTag(vh);
                    convertView = row;
                } else {
                    vh = (CsvRowViewHolder) convertView.getTag();
                }

                convertView.setBackgroundColor(position % 2 == 0 ? 0xFF131D31 : 0xFF0F172A);
                vh.tvRowIdx.setText("#" + (position + 1));

                List<String> dataRow = getItem(position);
                for (int c = 0; c < finalMaxCols; c++) {
                    String val = (dataRow != null && c < dataRow.size()) ? dataRow.get(c) : "";
                    vh.tvCells[c].setText(val);
                }

                return convertView;
            }
        };

        listView.setAdapter(adapter);

        // Container inside horizontal scroll view
        LinearLayout tableContainer = new LinearLayout(this);
        tableContainer.setOrientation(LinearLayout.VERTICAL);
        tableContainer.setBackgroundColor(0xFF0F172A);
        tableContainer.addView(headerRow);
        tableContainer.addView(listView);

        android.widget.HorizontalScrollView hScrollView = new android.widget.HorizontalScrollView(this);
        hScrollView.setBackgroundColor(0xFF0F172A);
        hScrollView.addView(tableContainer);

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("📋 Tabel Data CSV (" + totalRows + " Baris)")
            .setView(hScrollView)
            .setPositiveButton("Tutup", null)
            .create();

        dialog.setOnShowListener(d -> {
            int screenH = getResources().getDisplayMetrics().heightPixels;
            listView.setMinimumHeight((int)(screenH * 0.65f));
        });
        dialog.show();
    }

    private void reapplyCsvColumnMapping() {
        if (rawCsvRows == null || rawCsvRows.isEmpty()) return;

        int nameCol = spinnerCsvNameCol != null ? spinnerCsvNameCol.getSelectedItemPosition() : 0;
        int phoneCol = spinnerCsvPhoneCol != null ? spinnerCsvPhoneCol.getSelectedItemPosition() : 1;
        boolean skipHeader = chkCsvHasHeader != null && chkCsvHasHeader.isChecked();
        final boolean excludeSuspicious = (chkExcludeSuspicious == null || chkExcludeSuspicious.isChecked());
        final int countryFilterPos = (spinnerCountryFilter != null) ? spinnerCountryFilter.getSelectedItemPosition() : 0;

        List<ContactEntry> fileContacts = new ArrayList<>();
        int startRow = skipHeader ? 1 : 0;
        for (int i = startRow; i < rawCsvRows.size(); i++) {
            List<String> row = rawCsvRows.get(i);
            String name = (nameCol >= 0 && nameCol < row.size()) ? row.get(nameCol).trim() : "";
            String phone = (phoneCol >= 0 && phoneCol < row.size()) ? row.get(phoneCol).trim() : "";
            if (phone.isEmpty()) continue;
            if (name.isEmpty()) name = "Kontak " + phone;
            ContactEntry entry = new ContactEntry(name, phone);
            fileContacts.add(entry);
        }

        List<ContactEntry> toSave = new ArrayList<>();
        List<ContactEntry> dupList = new ArrayList<>();
        List<ContactEntry> suspiciousList = new ArrayList<>();

        for (ContactEntry c : fileContacts) {
            boolean isSusp = false;
            for (String p : c.phones) {
                if (isSuspiciousPhone(p) || !matchesCountryFilter(p, countryFilterPos)) {
                    isSusp = true;
                    break;
                }
            }

            if (isSusp) {
                suspiciousList.add(c);
                if (excludeSuspicious) {
                    continue; // Skip saving suspicious numbers
                }
            }

            boolean dup = false;
            for (String p : c.phones) {
                if (existingPhonesForPreview != null && existingPhonesForPreview.contains(normalizePhone(p))) {
                    dup = true;
                    break;
                }
            }
            if (dup) dupList.add(c); else toSave.add(c);
        }

        analyzedToSave = toSave;
        allParsedContacts = fileContacts;
        final int total = fileContacts.size();
        final int dupCount = dupList.size();
        final int suspCount = suspiciousList.size();
        final int uniqueCount = toSave.size();

        btnProcess.setEnabled(uniqueCount > 0);
        btnPreviewImport.setEnabled(total > 0);
        tvAnalyzeResult.setText(
            "📊 HASIL ANALISIS FILE (PEMETAAN KUSTOM)\n\n" +
            "📁 Nama File              : " + getFileName(selectedFileUri) + "\n" +
            "📝 Format                 : CSV Kustom [Nama: Kolom " + (nameCol + 1) + ", No HP: Kolom " + (phoneCol + 1) + "]\n" +
            "📁 Total kontak di file   : " + total + " kontak\n" +
            "🔍 Dicek duplikat dari    : " + (lastAnalyzeSrcLabel.isEmpty() ? "HP" : lastAnalyzeSrcLabel) + "\n" +
            (suspCount > 0 ? "⚠️ Nomor mencurigakan/aneh : " + suspCount + " kontak (" + (excludeSuspicious ? "Akan di-skip" : "Tetap disimpan") + ")\n" : "") +
            "🔁 Sudah ada (skip)       : " + dupCount + " kontak\n" +
            "✨ Baru & unik             : " + uniqueCount + " kontak\n\n" +
            (uniqueCount > 0
                ? "➡️ Langkah 3: Pilih tujuan simpan\n   lalu tap 'Simpan Kontak Unik'."
                : "ℹ️ Semua kontak valid di file sudah ada di HP.")
        );
        if (uniqueCount == 0) tvStatus.setText("ℹ️ Tidak ada kontak baru valid untuk disimpan.");
        else tvStatus.setText("Siap menyimpan " + uniqueCount + " kontak unik.");
    }

    private List<String> parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder curVal = new StringBuilder();
        int len = line.length();
        for (int i = 0; i < len; i++) {
            char c = line.charAt(i);
            if (c == '\"') {
                if (inQuotes && i + 1 < len && line.charAt(i + 1) == '\"') {
                    curVal.append('\"');
                    i++; // skip next quote
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                result.add(curVal.toString().trim());
                curVal.setLength(0);
            } else {
                curVal.append(c);
            }
        }
        result.add(curVal.toString().trim());
        return result;
    }

    private boolean isPhoneHeader(String s) {
        if (s == null) return false;
        String h = s.toLowerCase().replaceAll("[^a-z0-9]", "");
        if (h.contains("label") || h.contains("type")) return false;
        return h.contains("phone") || h.contains("tel") || h.contains("hp") || h.contains("nomor") || h.contains("mobile") || h.contains("contact");
    }

    private boolean isNameHeader(String s) {
        if (s == null) return false;
        String h = s.toLowerCase().replaceAll("[^a-z0-9]", "");
        if (h.contains("phonetic")) return false;
        return h.contains("name") || h.contains("nama") || h.contains("display") || h.contains("given") || h.contains("family") || h.contains("first") || h.contains("last");
    }

    private boolean isLikelyPhone(String val) {
        if (val == null) return false;
        String trimmed = val.trim();
        if (trimmed.isEmpty()) return false;
        if (!trimmed.matches("^[0-9+\\s\\-().]+$")) return false;
        int digitCount = 0;
        for (int i = 0; i < trimmed.length(); i++) {
            if (Character.isDigit(trimmed.charAt(i))) {
                digitCount++;
            }
        }
        return digitCount >= 7 && digitCount <= 15;
    }

    private boolean isLikelyName(String val) {
        if (val == null) return false;
        String trimmed = val.trim();
        if (trimmed.isEmpty()) return false;
        if (isLikelyPhone(trimmed)) return false;
        boolean hasLetter = false;
        for (int i = 0; i < trimmed.length(); i++) {
            if (Character.isLetter(trimmed.charAt(i))) {
                hasLetter = true;
                break;
            }
        }
        return hasLetter;
    }

    private int calculateColumnPhoneScore(List<List<String>> rows, int colIdx) {
        if (rows == null || rows.isEmpty()) return 0;
        int score = 0;
        int validPhones = 0;
        int totalChecked = 0;
        int startRow = (rows.size() > 1) ? 1 : 0;
        int limit = Math.min(rows.size(), 30);

        for (int r = startRow; r < limit; r++) {
            List<String> row = rows.get(r);
            if (colIdx >= row.size()) continue;
            String val = row.get(colIdx).trim();
            if (val.isEmpty()) continue;
            totalChecked++;

            // Count digits and check formatting
            String cleaned = val.replaceAll("[^0-9+]", "");
            int digitCount = 0;
            for (int k = 0; k < cleaned.length(); k++) {
                if (Character.isDigit(cleaned.charAt(k))) digitCount++;
            }

            if (digitCount >= 8 && digitCount <= 15) {
                validPhones++;
                score += 10;
                // High confidence phone prefixes (Indonesian & International)
                if (cleaned.startsWith("+628") || cleaned.startsWith("628") || cleaned.startsWith("08")) {
                    score += 20;
                } else if (cleaned.startsWith("+") || cleaned.startsWith("0")) {
                    score += 10;
                }
                // Pure numeric / clean phone bonus
                if (val.matches("^[0-9+\\s\\-().]+$")) {
                    score += 10;
                }
            } else if (digitCount > 0 && (digitCount < 6 || digitCount > 18)) {
                // Penalize IDs, zip codes, timestamps
                score -= 20;
            }
        }

        if (totalChecked > 0 && (float) validPhones / totalChecked >= 0.75f) {
            score += 100; // Overwhelmingly valid phone column
        }
        return Math.max(0, score);
    }

    private int calculateColumnNameScore(List<List<String>> rows, int colIdx, int bestPhoneCol) {
        if (rows == null || rows.isEmpty() || colIdx == bestPhoneCol) return 0;
        int score = 0;
        int totalChecked = 0;
        int azkaOccurrences = 0;
        Set<String> distinctValues = new HashSet<>();
        int startRow = (rows.size() > 1) ? 1 : 0;
        int limit = Math.min(rows.size(), 30);

        for (int r = startRow; r < limit; r++) {
            List<String> row = rows.get(r);
            if (colIdx >= row.size()) continue;
            String val = row.get(colIdx).trim();
            if (val.isEmpty()) continue;
            totalChecked++;
            distinctValues.add(val.toLowerCase());

            // Check for AZKA keyword (case-insensitive, all variations)
            if (val.toLowerCase().contains("azka")) {
                azkaOccurrences++;
            }

            // Check if looks like a name
            int letterCount = 0;
            for (int k = 0; k < val.length(); k++) {
                if (Character.isLetter(val.charAt(k))) letterCount++;
            }

            if (letterCount >= 2) {
                score += 10;
                // Multi-word name bonus (e.g. "Budi Santoso", "Andi Wijaya")
                if (val.contains(" ") && val.length() >= 5) {
                    score += 10;
                }
                // Penalize email, URL, or numbers
                if (val.contains("@") || val.contains("http") || val.contains(".com")) {
                    score -= 30;
                }
            } else if (isLikelyPhone(val)) {
                score -= 30;
            }
        }

        // Heavy penalty if column repeatedly contains "azka" across samples
        if (azkaOccurrences > 0) {
            score -= (azkaOccurrences * 35);
        }

        // Check diversity: if multiple rows all have the same 1 or 2 static values, it's a fixed tag/category, NOT names!
        if (totalChecked > 3 && distinctValues.size() <= 2) {
            score -= 100; // Constant tag column penalty
        } else if (totalChecked > 3 && (float) distinctValues.size() / totalChecked >= 0.7f) {
            score += 60; // High uniqueness/diversity bonus (true contact names)
        }

        return Math.max(0, score);
    }

    private int saveContactsBatch(List<ContactEntry> contacts, String accountType, String accountName) {
        // High-performance bulk insert:
        // 1. Chunk size = 300 contacts (~900 operations per batch) for optimal Binder throughput
        // 2. CALLER_IS_SYNCADAPTER = "true" to eliminate redundant broadcast overhead
        // 3. AGGREGATION_MODE_DISABLED to bypass expensive CPU contact-matching during insertion
        int saved = 0;
        final int CHUNK = 300;
        final int total = contacts.size();
        int processed = 0;

        final Uri rawContactsUri = ContactsContract.RawContacts.CONTENT_URI.buildUpon()
            .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build();
        final Uri dataUri = ContactsContract.Data.CONTENT_URI.buildUpon()
            .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build();

        while (processed < total) {
            int end = Math.min(processed + CHUNK, total);
            List<ContactEntry> chunk = contacts.subList(processed, end);

            ArrayList<ContentProviderOperation> ops = new ArrayList<>();
            int opIndex = 0;

            for (ContactEntry c : chunk) {
                int rawContactOpIndex = opIndex;
                ops.add(ContentProviderOperation.newInsert(rawContactsUri)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, accountType)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, accountName)
                    .withValue(ContactsContract.RawContacts.AGGREGATION_MODE,
                        ContactsContract.RawContacts.AGGREGATION_MODE_DISABLED)
                    .build());
                opIndex++;

                ops.add(ContentProviderOperation.newInsert(dataUri)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactOpIndex)
                    .withValue(ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, c.name).build());
                opIndex++;

                for (String phone : c.phones) {
                    ops.add(ContentProviderOperation.newInsert(dataUri)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactOpIndex)
                        .withValue(ContactsContract.Data.MIMETYPE,
                            ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
                        .withValue(ContactsContract.CommonDataKinds.Phone.TYPE,
                            ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE).build());
                    opIndex++;
                }
            }

            try {
                getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
                saved += chunk.size();
            } catch (Exception e) {
                // Fallback: simpan satu per satu jika batch gagal
                for (ContactEntry c : chunk) {
                    try {
                        ArrayList<ContentProviderOperation> single = new ArrayList<>();
                        single.add(ContentProviderOperation.newInsert(rawContactsUri)
                            .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, accountType)
                            .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, accountName)
                            .withValue(ContactsContract.RawContacts.AGGREGATION_MODE,
                                ContactsContract.RawContacts.AGGREGATION_MODE_DISABLED)
                            .build());
                        single.add(ContentProviderOperation.newInsert(dataUri)
                            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                            .withValue(ContactsContract.Data.MIMETYPE,
                                ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                            .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, c.name).build());
                        for (String phone : c.phones) {
                            single.add(ContentProviderOperation.newInsert(dataUri)
                                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                                .withValue(ContactsContract.Data.MIMETYPE,
                                    ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
                                .withValue(ContactsContract.CommonDataKinds.Phone.TYPE,
                                    ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE).build());
                        }
                        getContentResolver().applyBatch(ContactsContract.AUTHORITY, single);
                        saved++;
                    } catch (Exception ignored) {}
                }
            }

            processed = end;
            final int prog = processed, tot = total, sv = saved;
            mainHandler.post(() ->
                tvStatus.setText("⏳ Menyimpan kontak... " + prog + "/" + tot + " (" + sv + " berhasil)")
            );
        }
        return saved;
    }

    private int saveContacts(List<ContactEntry> contacts, String accountType, String accountName) {
        return saveContactsBatch(contacts, accountType, accountName);
    }

    private String safe(String s) { return s == null ? "" : s.replace("\"", "'"); }

    // ─── VALIDASI NOMOR & FILTER NEGARA ──────────────────────────────────────────

    private boolean isSuspiciousPhone(String phone) {
        if (phone == null) return true;
        String trimmed = phone.trim();
        if (trimmed.isEmpty()) return true;

        String digits = trimmed.replaceAll("[^0-9]", "");
        int len = digits.length();

        // 1. WhatsApp Group JID format (120xxxxxxxx, typically 14-20 digits)
        if (digits.startsWith("120") && len >= 14) return true;

        // 2. Too short (< 7 digit) or too long (> 15 digit)
        if (len < 7 || len > 15) return true;

        // 3. Repetitive dummy numbers (e.g. 0000000, 11111111)
        if (digits.matches("^(\\d)\\1+$")) return true;

        // 4. Letters inside phone number
        for (int i = 0; i < trimmed.length(); i++) {
            if (Character.isLetter(trimmed.charAt(i))) return true;
        }

        return false;
    }

    private boolean matchesCountryFilter(String phone, int filterPosition) {
        if (phone == null) return false;
        if (filterPosition == 0) return true; // Semua Negara (Valid)

        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return false;

        // Mode 1: Indonesia (+62, 62, 08, 021, etc.)
        boolean isIndo = digits.startsWith("62") || digits.startsWith("08") || digits.startsWith("02") || digits.startsWith("03") || digits.startsWith("04") || digits.startsWith("07") || digits.startsWith("09");
        if (filterPosition == 1) return isIndo;

        // Mode 2: Indonesia + Malaysia (+60, 60, 01)
        boolean isMalay = digits.startsWith("60") || (digits.startsWith("01") && digits.length() >= 9 && digits.length() <= 11);
        if (filterPosition == 2) return isIndo || isMalay;

        return true;
    }

    private Set<String> extractPhoneNumbersFromFile(Uri uri) throws Exception {
        Set<String> extracted = new LinkedHashSet<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(getContentResolver().openInputStream(uri), "UTF-8"));
        String line;

        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;

            // If it looks like a vCard line
            if (line.toUpperCase().startsWith("TEL")) {
                int colonIdx = line.indexOf(':');
                if (colonIdx != -1 && colonIdx + 1 < line.length()) {
                    String p = line.substring(colonIdx + 1).trim();
                    String norm = normalizePhone(p);
                    if (!norm.isEmpty() && !isSuspiciousPhone(p)) extracted.add(norm);
                }
                continue;
            }

            // If CSV or comma/semicolon/tab separated
            List<String> tokens = parseCsvLine(line);
            if (tokens.size() > 1) {
                for (String t : tokens) {
                    if (isLikelyPhone(t)) {
                        String norm = normalizePhone(t);
                        if (!norm.isEmpty() && !isSuspiciousPhone(t)) extracted.add(norm);
                    }
                }
            } else {
                // Raw line or single phone number
                java.util.regex.Pattern p = java.util.regex.Pattern.compile("\\+?[0-9\\-\\s()]{7,18}");
                java.util.regex.Matcher m = p.matcher(line);
                while (m.find()) {
                    String raw = m.group().trim();
                    String norm = normalizePhone(raw);
                    if (!norm.isEmpty() && !isSuspiciousPhone(raw)) extracted.add(norm);
                }
            }
        }
        reader.close();
        return extracted;
    }

    // ─── HAPUS KONTAK DARI FILE ───────────────────────────────────────────────────

    private void openDeleteByFilePage() {
        showPage(layoutDeleteByFile);
        tvFileNameForDelete.setText(selectedDeleteFileUri != null ? "📄 " + getFileName(selectedDeleteFileUri) : "Belum ada file dipilih");
        tvDeleteByFileStatus.setText(selectedDeleteFileUri != null ? "Siap menganalisis." : "Pilih file kontak terlebih dahulu.");
        progressDeleteByFile.setVisibility(View.GONE);
        layoutDeleteByFileResult.setVisibility(View.GONE);
        btnAnalyzeFileForDelete.setEnabled(selectedDeleteFileUri != null);
        btnPreviewDeleteByFile.setEnabled(false);
        btnExecuteDeleteByFile.setEnabled(false);
    }

    private void pickFileForDelete() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "Pilih File untuk Dihapus"), REQ_PICK_FILE_FOR_DELETE);
    }

    private void analyzeFileForDelete() {
        if (selectedDeleteFileUri == null) {
            Toast.makeText(this, "Pilih file dulu!", Toast.LENGTH_SHORT).show();
            return;
        }

        btnAnalyzeFileForDelete.setEnabled(false);
        progressDeleteByFile.setVisibility(View.VISIBLE);
        layoutDeleteByFileResult.setVisibility(View.GONE);
        tvDeleteByFileStatus.setText("⏳ Mengekstrak nomor dari file & mencocokkan kontak di HP...");

        final boolean inclGoogle = chkDelFileGoogle.isChecked();
        final boolean inclPhone  = chkDelFilePhone.isChecked();
        final boolean inclSim    = chkDelFileSim.isChecked();
        final boolean inclWa     = chkDelFileWa.isChecked();
        final boolean inclWaBiz  = chkDelFileWaBusiness.isChecked();
        final boolean inclOther  = chkDelFileOther.isChecked();

        final int selDelGoogle = (spinnerDelFileGoogleAccount != null && spinnerDelFileGoogleAccount.getSelectedItemPosition() > 0)
            ? spinnerDelFileGoogleAccount.getSelectedItemPosition() : 0;
        final String selectedDelGoogleAccountName = (selDelGoogle > 0 && selDelGoogle - 1 < googleAccountNames.size())
            ? googleAccountNames.get(selDelGoogle - 1) : null;

        executor.execute(() -> {
            try {
                // 1. Ekstrak seluruh nomor unik dari file
                Set<String> filePhones = extractPhoneNumbersFromFile(selectedDeleteFileUri);

                // 2. Ambil kontak di HP sesuai target sumber
                List<PhoneContact> allContacts = getAllContactsWithSource();
                List<PhoneContact> targetContacts = new ArrayList<>();

                for (PhoneContact c : allContacts) {
                    String src = resolveSource(c.accountType);
                    if (src.equals(SRC_GOOGLE)) {
                        if (!inclGoogle) continue;
                        if (selectedDelGoogleAccountName != null && (c.accountName == null || !c.accountName.equalsIgnoreCase(selectedDelGoogleAccountName))) continue;
                    }
                    if (src.equals(SRC_PHONE)   && !inclPhone)  continue;
                    if (src.equals(SRC_SIM)     && !inclSim)    continue;
                    if (src.equals(SRC_WA)      && !inclWa)     continue;
                    if (src.equals(SRC_WA_BIZ)  && !inclWaBiz)  continue;
                    if (src.equals(SRC_OTHER)   && !inclOther)  continue;
                    targetContacts.add(c);
                }

                // 3. Match contacts
                List<PhoneContact> matched = new ArrayList<>();
                Set<String> matchedPhones = new HashSet<>();
                for (PhoneContact c : targetContacts) {
                    String norm = normalizePhone(c.phone);
                    if (!norm.isEmpty() && filePhones.contains(norm)) {
                        matched.add(c);
                        matchedPhones.add(norm);
                    }
                }

                final int totalFileNumbers = filePhones.size();
                final int totalMatched = matched.size();
                final int notFoundCount = Math.max(0, totalFileNumbers - matchedPhones.size());
                final List<PhoneContact> fMatched = matched;

                mainHandler.post(() -> {
                    contactsToDeleteByFile = fMatched;
                    progressDeleteByFile.setVisibility(View.GONE);
                    btnAnalyzeFileForDelete.setEnabled(true);
                    layoutDeleteByFileResult.setVisibility(View.VISIBLE);
                    btnPreviewDeleteByFile.setEnabled(totalMatched > 0);
                    btnExecuteDeleteByFile.setEnabled(totalMatched > 0);

                    tvDeleteByFileResult.setText(
                        "📊 HASIL ANALISIS HAPUS VIA FILE\n\n" +
                        "📁 File Sumber            : " + getFileName(selectedDeleteFileUri) + "\n" +
                        "📞 Total nomor unik file : " + totalFileNumbers + " nomor\n" +
                        "🔍 Kontak cocok di HP    : " + totalMatched + " kontak (DAPAT DIHAPUS)\n" +
                        "ℹ️ Tidak ada di HP       : " + notFoundCount + " nomor\n\n" +
                        (totalMatched > 0
                            ? "⚠️ Tap 'Hapus Kontak Cocok Sekarang' untuk mengeksekusi penghapusan."
                            : "✅ Tidak ditemukan kontak yang cocok di HP untuk dihapus.")
                    );
                    tvDeleteByFileStatus.setText(totalMatched > 0 ? "Ditemukan " + totalMatched + " kontak cocok siap dihapus." : "Tidak ada kontak cocok.");
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    progressDeleteByFile.setVisibility(View.GONE);
                    btnAnalyzeFileForDelete.setEnabled(true);
                    tvDeleteByFileStatus.setText("❌ Error: " + e.getMessage());
                });
            }
        });
    }

    private void showDeleteByFilePreviewDialog() {
        if (contactsToDeleteByFile.isEmpty()) {
            Toast.makeText(this, "Tidak ada kontak untuk ditampilkan.", Toast.LENGTH_SHORT).show();
            return;
        }

        float dp = getResources().getDisplayMetrics().density;
        int pad = (int)(12 * dp);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackgroundColor(0xFF0F172A);
        header.setPadding(pad, pad, pad, pad);

        TextView tvHdr = new TextView(this);
        tvHdr.setText("🔴 " + contactsToDeleteByFile.size() + " Kontak di HP Cocok dengan Nomor di File");
        tvHdr.setTextColor(0xFFEF4444);
        tvHdr.setTextSize(12);
        tvHdr.setTypeface(null, android.graphics.Typeface.BOLD);
        header.addView(tvHdr);

        android.widget.ListView listView = new android.widget.ListView(this);
        listView.setBackgroundColor(0xFF0F172A);
        listView.setDivider(null);
        listView.setDividerHeight((int)(4 * dp));

        android.widget.ArrayAdapter<PhoneContact> adapter = new android.widget.ArrayAdapter<PhoneContact>(
                this, 0, contactsToDeleteByFile) {
            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                ViewHolder vh;
                if (convertView == null) {
                    LinearLayout rowOuter = new LinearLayout(getContext());
                    rowOuter.setOrientation(LinearLayout.HORIZONTAL);

                    View bar = new View(getContext());
                    bar.setLayoutParams(new LinearLayout.LayoutParams((int)(4*dp), LinearLayout.LayoutParams.MATCH_PARENT));
                    bar.setBackgroundColor(0xFFEF4444);

                    LinearLayout inner = new LinearLayout(getContext());
                    inner.setOrientation(LinearLayout.VERTICAL);
                    inner.setBackgroundColor(0xFF1E293B);
                    inner.setPadding((int)(10*dp), (int)(8*dp), (int)(10*dp), (int)(8*dp));
                    inner.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

                    TextView tvName  = new TextView(getContext());
                    tvName.setTextSize(13); tvName.setTextColor(0xFFE2E8F0);
                    tvName.setTypeface(null, android.graphics.Typeface.BOLD);

                    TextView tvPhone = new TextView(getContext());
                    tvPhone.setTextSize(11); tvPhone.setTextColor(0xFF94A3B8);

                    TextView tvSrc   = new TextView(getContext());
                    tvSrc.setTextSize(10); tvSrc.setTextColor(0xFFEF4444);

                    inner.addView(tvName);
                    inner.addView(tvPhone);
                    inner.addView(tvSrc);

                    rowOuter.addView(bar);
                    rowOuter.addView(inner);
                    convertView = rowOuter;

                    vh = new ViewHolder();
                    vh.bar = bar; vh.tvName = tvName; vh.tvPhone = tvPhone; vh.tvSrc = tvSrc;
                    convertView.setTag(vh);
                } else {
                    vh = (ViewHolder) convertView.getTag();
                }

                PhoneContact c = getItem(position);
                if (c != null) {
                    vh.tvName.setText(c.name != null && !c.name.isEmpty() ? c.name : "(Tanpa Nama)");
                    vh.tvPhone.setText("📞 " + (c.phone != null ? c.phone : "-"));
                    String src = resolveSource(c.accountType);
                    if (src.equals(SRC_GOOGLE) && c.accountName != null) {
                        vh.tvSrc.setText("🔴 AKAN DIHAPUS — Google (" + c.accountName + ")");
                    } else {
                        vh.tvSrc.setText("🔴 AKAN DIHAPUS — " + src);
                    }
                }
                return convertView;
            }
        };

        listView.setAdapter(adapter);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.addView(header);
        root.addView(listView);

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("👁️ Preview Kontak Akan Dihapus (" + contactsToDeleteByFile.size() + " Kontak)")
            .setView(root)
            .setPositiveButton("Tutup", null)
            .create();

        dialog.setOnShowListener(d -> {
            int screenH = getResources().getDisplayMetrics().heightPixels;
            listView.setMinimumHeight((int)(screenH * 0.65f));
        });
        dialog.show();
    }

    private void executeDeleteByFile() {
        if (contactsToDeleteByFile.isEmpty()) {
            Toast.makeText(this, "Tidak ada kontak untuk dihapus.", Toast.LENGTH_SHORT).show();
            return;
        }

        final int totalDel = contactsToDeleteByFile.size();
        new AlertDialog.Builder(this)
            .setTitle("⚠️ Konfirmasi Hapus " + totalDel + " Kontak")
            .setMessage("Apakah Anda YAKIN ingin MENGHAPUS " + totalDel + " kontak di HP yang nomornya cocok dengan file ini?\n\nKontak akan dihapus permanen dari HP.")
            .setPositiveButton("🗑️ Ya, Hapus Sekarang", (d, w) -> {
                btnExecuteDeleteByFile.setEnabled(false);
                btnPreviewDeleteByFile.setEnabled(false);
                progressDeleteByFile.setVisibility(View.VISIBLE);
                tvDeleteByFileStatus.setText("⏳ Sedang menghapus " + totalDel + " kontak...");

                executor.execute(() -> {
                    try {
                        int deleted = deleteContactsBatch(contactsToDeleteByFile);
                        mainHandler.post(() -> {
                            progressDeleteByFile.setVisibility(View.GONE);
                            contactsToDeleteByFile.clear();
                            layoutDeleteByFileResult.setVisibility(View.GONE);
                            tvDeleteByFileStatus.setText("✅ Berhasil menghapus " + deleted + " kontak dari HP!");
                            Toast.makeText(MainActivity.this, "✅ " + deleted + " kontak berhasil dihapus!", Toast.LENGTH_LONG).show();
                        });
                    } catch (Exception e) {
                        mainHandler.post(() -> {
                            progressDeleteByFile.setVisibility(View.GONE);
                            btnExecuteDeleteByFile.setEnabled(true);
                            btnPreviewDeleteByFile.setEnabled(true);
                            tvDeleteByFileStatus.setText("❌ Gagal menghapus: " + e.getMessage());
                        });
                    }
                });
            })
            .setNegativeButton("Batal", null)
            .show();
    }

    private int deleteContactsBatch(List<PhoneContact> contacts) {
        int deleted = 0;
        final int CHUNK = 500;
        final int total = contacts.size();
        int processed = 0;

        while (processed < total) {
            int end = Math.min(processed + CHUNK, total);
            List<PhoneContact> chunk = contacts.subList(processed, end);
            ArrayList<ContentProviderOperation> ops = new ArrayList<>();

            for (PhoneContact c : chunk) {
                ops.add(ContentProviderOperation.newDelete(
                    ContactsContract.RawContacts.CONTENT_URI.buildUpon()
                        .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
                        .build())
                    .withSelection(ContactsContract.RawContacts._ID + "=?",
                        new String[]{String.valueOf(c.rawContactId)})
                    .build());
            }

            try {
                getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
                deleted += chunk.size();
            } catch (Exception e) {
                // Fallback: satu per satu
                for (PhoneContact c : chunk) {
                    try {
                        getContentResolver().delete(
                            ContactsContract.RawContacts.CONTENT_URI,
                            ContactsContract.RawContacts._ID + "=?",
                            new String[]{String.valueOf(c.rawContactId)}
                        );
                        deleted++;
                    } catch (Exception ignored) {}
                }
            }
            processed = end;
        }
        return deleted;
    }

    private void showImportPreviewDialog() {
        if (allParsedContacts == null || allParsedContacts.isEmpty()) {
            Toast.makeText(this, "Tidak ada data untuk di-preview.", Toast.LENGTH_SHORT).show();
            return;
        }

        btnPreviewImport.setEnabled(false);
        Toast.makeText(this, "Menyiapkan preview...", Toast.LENGTH_SHORT).show();

        final int countryFilterPos = (spinnerCountryFilter != null) ? spinnerCountryFilter.getSelectedItemPosition() : 0;

        executor.execute(() -> {
            List<ImportPreviewItem> previewList = new ArrayList<>(allParsedContacts.size());
            int countNew = 0, countDup = 0, countSuspicious = 0;

            for (ContactEntry c : allParsedContacts) {
                if (c == null) continue;
                ImportPreviewItem item = new ImportPreviewItem();
                item.name = (c.name != null && !c.name.trim().isEmpty()) ? c.name.trim() : "(Tanpa Nama)";

                StringBuilder sbPhones = new StringBuilder();
                boolean isDup = false;
                boolean isSusp = false;

                for (String p : c.phones) {
                    if (p != null && !p.trim().isEmpty()) {
                        if (sbPhones.length() > 0) sbPhones.append(", ");
                        sbPhones.append(p.trim());

                        if (isSuspiciousPhone(p) || !matchesCountryFilter(p, countryFilterPos)) {
                            isSusp = true;
                        }
                        if (!isDup && existingPhonesForPreview != null && existingPhonesForPreview.contains(normalizePhone(p))) {
                            isDup = true;
                        }
                    }
                }
                item.phoneDisplay = sbPhones.length() > 0 ? "📞 " + sbPhones.toString() : "📞 -";

                if (isSusp) {
                    item.status = 2;
                    item.statusLabel = "⚠️ MENCURIGAKAN (ID Grup / Format Aneh)";
                    countSuspicious++;
                } else if (isDup) {
                    item.status = 1;
                    item.statusLabel = "🔴 DUPLIKAT (Skip)";
                    countDup++;
                } else {
                    item.status = 0;
                    item.statusLabel = "🟢 BARU (Simpan)";
                    countNew++;
                }
                previewList.add(item);
            }

            // Sort: Mencurigakan (status=2) PINNED AT TOP, then Baru (0), then Duplikat (1)
            previewList.sort((a, b) -> {
                int rankA = (a.status == 2) ? 0 : (a.status == 0 ? 1 : 2);
                int rankB = (b.status == 2) ? 0 : (b.status == 0 ? 1 : 2);
                if (rankA != rankB) return Integer.compare(rankA, rankB);
                return a.name.compareToIgnoreCase(b.name);
            });

            final int fCountNew = countNew;
            final int fCountDup = countDup;
            final int fCountSusp = countSuspicious;
            final List<ImportPreviewItem> fPreviewList = previewList;

            mainHandler.post(() -> {
                btnPreviewImport.setEnabled(true);

                float dp = getResources().getDisplayMetrics().density;
                int pad = (int)(12 * dp);

                // Legend header
                LinearLayout header = new LinearLayout(MainActivity.this);
                header.setOrientation(LinearLayout.VERTICAL);
                header.setBackgroundColor(0xFF0F172A);
                header.setPadding(pad, pad, pad, pad);

                LinearLayout legendRow = new LinearLayout(MainActivity.this);
                legendRow.setOrientation(LinearLayout.HORIZONTAL);
                addLegendItem(legendRow, "⚠️ Mencurigakan  ", 0xFFF59E0B);
                addLegendItem(legendRow, "🟢 Baru  ", 0xFF34D399);
                addLegendItem(legendRow, "🔴 Duplikat", 0xFFEF4444);
                header.addView(legendRow);

                TextView tvCount = new TextView(MainActivity.this);
                tvCount.setText("Total: " + fPreviewList.size() + " kontak  |  ⚠️ " + fCountSusp + " Mencurigakan  |  🟢 " + fCountNew + " Baru  |  🔴 " + fCountDup + " Duplikat");
                tvCount.setTextColor(0xFF64748B);
                tvCount.setTextSize(11);
                tvCount.setPadding(0, (int)(6*dp), 0, 0);
                header.addView(tvCount);

                android.widget.ListView listView = new android.widget.ListView(MainActivity.this);
                listView.setBackgroundColor(0xFF0F172A);
                listView.setDivider(null);
                listView.setDividerHeight((int)(4 * dp));

                android.widget.ArrayAdapter<ImportPreviewItem> adapter = new android.widget.ArrayAdapter<ImportPreviewItem>(
                        MainActivity.this, 0, fPreviewList) {
                    @Override
                    public android.view.View getView(int position, android.view.View convertView, android.view.ViewGroup parent) {
                        ViewHolder vh;
                        if (convertView == null) {
                            LinearLayout rowOuter = new LinearLayout(getContext());
                            rowOuter.setOrientation(LinearLayout.HORIZONTAL);

                            android.view.View bar = new android.view.View(getContext());
                            LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams((int)(4*dp), LinearLayout.LayoutParams.MATCH_PARENT);
                            bar.setLayoutParams(barLp);

                            LinearLayout inner = new LinearLayout(getContext());
                            inner.setOrientation(LinearLayout.VERTICAL);
                            inner.setBackgroundColor(0xFF1E293B);
                            inner.setPadding((int)(10*dp), (int)(8*dp), (int)(10*dp), (int)(8*dp));
                            inner.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

                            TextView tvName  = new TextView(getContext());
                            tvName.setTextSize(13); tvName.setTextColor(0xFFE2E8F0);
                            tvName.setTypeface(null, android.graphics.Typeface.BOLD);

                            TextView tvPhone = new TextView(getContext());
                            tvPhone.setTextSize(11); tvPhone.setTextColor(0xFF94A3B8);

                            TextView tvSrc   = new TextView(getContext());
                            tvSrc.setTextSize(10);

                            inner.addView(tvName);
                            inner.addView(tvPhone);
                            inner.addView(tvSrc);

                            rowOuter.addView(bar);
                            rowOuter.addView(inner);
                            convertView = rowOuter;

                            vh = new ViewHolder();
                            vh.bar = bar; vh.tvName = tvName; vh.tvPhone = tvPhone; vh.tvSrc = tvSrc;
                            convertView.setTag(vh);
                        } else {
                            vh = (ViewHolder) convertView.getTag();
                        }

                        ImportPreviewItem item = getItem(position);
                        if (item != null) {
                            int color;
                            if (item.status == 2) color = 0xFFF59E0B;
                            else if (item.status == 1) color = 0xFFEF4444;
                            else color = 0xFF34D399;

                            vh.bar.setBackgroundColor(color);
                            vh.tvName.setText(item.name);
                            vh.tvPhone.setText(item.phoneDisplay);
                            vh.tvSrc.setText(item.statusLabel);
                            vh.tvSrc.setTextColor(color);
                        }

                        return convertView;
                    }
                };

                listView.setAdapter(adapter);

                LinearLayout root = new LinearLayout(MainActivity.this);
                root.setOrientation(LinearLayout.VERTICAL);
                root.addView(header);
                root.addView(listView);

                AlertDialog dialog = new AlertDialog.Builder(MainActivity.this)
                    .setTitle("👁️ Preview Import (" + fPreviewList.size() + " Kontak)")
                    .setView(root)
                    .setPositiveButton("Tutup", null)
                    .create();

                dialog.setOnShowListener(d -> {
                    int screenH = getResources().getDisplayMetrics().heightPixels;
                    listView.setMinimumHeight((int)(screenH * 0.65f));
                });
                dialog.show();
            });
        });
    }

    static class ImportPreviewItem {
        String name;
        String phoneDisplay;
        int status; // 0 = BARU, 1 = DUPLIKAT, 2 = MENCURIGAKAN
        String statusLabel;
    }

    static class ContactEntry {
        String name = "";
        List<String> phones = new ArrayList<>();
        ContactEntry() {}
        ContactEntry(String name, String phone) {
            this.name = name;
            if (phone != null && !phone.isEmpty()) this.phones.add(phone);
        }
    }
    static class PhoneContact { String name, phone, accountType, accountName; long rawContactId; }
    static class DuplicateGroup {
        String normalizedPhone; List<PhoneContact> contacts;
        DuplicateGroup(String p, List<PhoneContact> c) { normalizedPhone = p; contacts = c; }
    }
}