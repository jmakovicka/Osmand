package net.osmand.plus.activities;

import java.io.File;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import net.osmand.plus.NavigationService;
import net.osmand.plus.OsmandSettings;
import net.osmand.plus.ProgressDialogImplementation;
import net.osmand.plus.R;
import net.osmand.plus.ResourceManager;
import net.osmand.plus.OsmandSettings.DayNightMode;
import net.osmand.plus.OsmandSettings.MetricsConstants;
import net.osmand.plus.OsmandSettings.OsmandPreference;
import net.osmand.plus.activities.RouteProvider.RouteService;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.app.AlertDialog.Builder;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.DialogInterface.OnClickListener;
import android.content.pm.ActivityInfo;
import android.location.LocationManager;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.widget.Toast;

public class SettingsActivity extends PreferenceActivity implements OnPreferenceChangeListener, OnPreferenceClickListener {
	private final static String VECTOR_MAP = "#VECTOR_MAP"; //$NON-NLS-1$
	
	
	private Preference saveCurrentTrack;
	private Preference reloadIndexes;
	private Preference downloadIndexes;

	private EditTextPreference applicationDir;
	private ListPreference tileSourcePreference;
	
	private CheckBoxPreference routeServiceEnabled;
	private BroadcastReceiver broadcastReceiver;
	
	private ProgressDialog progressDlg;
	
	private OsmandSettings osmandSettings;
	
	private Map<String, Preference> screenPreferences = new LinkedHashMap<String, Preference>();
	private Map<String, OsmandPreference<Boolean>> booleanPreferences = new LinkedHashMap<String, OsmandPreference<Boolean>>();
	private Map<String, OsmandPreference<?>> listPreferences = new LinkedHashMap<String, OsmandPreference<?>>();
	private Map<String, OsmandPreference<String>> editTextPreferences = new LinkedHashMap<String, OsmandPreference<String>>();
	private Map<String, Map<String, ?>> listPrefValues = new LinkedHashMap<String, Map<String, ?>>();
	
	
	
	private void registerBooleanPreference(OsmandPreference<Boolean> b, PreferenceScreen screen){
		CheckBoxPreference p = (CheckBoxPreference) screen.findPreference(b.getId());
		p.setOnPreferenceChangeListener(this);
		screenPreferences.put(b.getId(), p);
		booleanPreferences.put(b.getId(), b);
	}
	
	private <T> void registerListPreference(OsmandPreference<T> b, PreferenceScreen screen, String[] names, T[] values){
		ListPreference p = (ListPreference) screen.findPreference(b.getId());
		p.setOnPreferenceChangeListener(this);
		LinkedHashMap<String, Object> vals = new LinkedHashMap<String, Object>();
		screenPreferences.put(b.getId(), p);
		listPreferences.put(b.getId(), b);
		listPrefValues.put(b.getId(), vals);
		assert names.length == values.length;
		for(int i=0; i<names.length; i++){
			vals.put(names[i], values[i]);
		}
	}
	
	private void registerEditTextPreference(OsmandPreference<String> b, PreferenceScreen screen){
		EditTextPreference p = (EditTextPreference) screen.findPreference(b.getId());
		p.setOnPreferenceChangeListener(this);
		screenPreferences.put(b.getId(), p);
		editTextPreferences.put(b.getId(), b);
	}
	
	private  void registerTimeListPreference(OsmandPreference<Integer> b, PreferenceScreen screen, int[] seconds, int[] minutes, int coeff){
		int minutesLength = minutes == null? 0 : minutes.length;
    	int secondsLength = seconds == null? 0 : seconds.length;
    	Integer[] ints = new Integer[secondsLength + minutesLength];
		String[] intDescriptions = new String[ints.length];
		for (int i = 0; i < secondsLength; i++) {
			ints[i] = seconds[i] * coeff;
			intDescriptions[i] = seconds[i] + " " + getString(R.string.int_seconds); //$NON-NLS-1$
		}
		for (int i = 0; i < minutesLength; i++) {
			ints[secondsLength + i] = (minutes[i] * 60) * coeff;
			intDescriptions[secondsLength + i] = minutes[i] + " " + getString(R.string.int_min); //$NON-NLS-1$
		}
		registerListPreference(b, screen, intDescriptions, ints);
	}
	
	private Set<String> getVoiceFiles(){
		// read available voice data
		File extStorage = osmandSettings.extendOsmandPath(ResourceManager.VOICE_PATH);
		Set<String> setFiles = new LinkedHashSet<String>();
		if (extStorage.exists()) {
			for (File f : extStorage.listFiles()) {
				if (f.isDirectory()) {
					setFiles.add(f.getName());
				}
			}
		}
		return setFiles;
	}
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.settings_pref);
		String[] entries;
		String[] entrieValues;
		PreferenceScreen screen = getPreferenceScreen();
		osmandSettings = OsmandSettings.getOsmandSettings(this);
		
		registerBooleanPreference(osmandSettings.SHOW_VIEW_ANGLE,screen); 
	    registerBooleanPreference(osmandSettings.USE_TRACKBALL_FOR_MOVEMENTS,screen); 
	    registerBooleanPreference(osmandSettings.USE_HIGH_RES_MAPS,screen); 
	    registerBooleanPreference(osmandSettings.USE_ENGLISH_NAMES,screen); 
	    registerBooleanPreference(osmandSettings.AUTO_ZOOM_MAP,screen); 
	    registerBooleanPreference(osmandSettings.SAVE_TRACK_TO_GPX,screen); 
	    registerBooleanPreference(osmandSettings.DEBUG_RENDERING_INFO,screen); 
	    registerBooleanPreference(osmandSettings.USE_STEP_BY_STEP_RENDERING,screen); 
	    registerBooleanPreference(osmandSettings.FAST_ROUTE_MODE,screen);
	    registerBooleanPreference(osmandSettings.USE_OSMAND_ROUTING_SERVICE_ALWAYS,screen); 
	    registerBooleanPreference(osmandSettings.USE_INTERNET_TO_DOWNLOAD_TILES,screen);
	    
		registerEditTextPreference(osmandSettings.USER_NAME, screen);
		registerEditTextPreference(osmandSettings.USER_PASSWORD, screen);
		
		
		
		// List preferences
		registerListPreference(osmandSettings.ROTATE_MAP, screen, 
				new String[]{getString(R.string.rotate_map_none_opt), getString(R.string.rotate_map_bearing_opt), getString(R.string.rotate_map_compass_opt)},
				new Integer[]{OsmandSettings.ROTATE_MAP_NONE, OsmandSettings.ROTATE_MAP_BEARING, OsmandSettings.ROTATE_MAP_COMPASS});
		
		registerListPreference(osmandSettings.MAP_SCREEN_ORIENTATION, screen, 
				new String[] {getString(R.string.map_orientation_portrait), getString(R.string.map_orientation_landscape), getString(R.string.map_orientation_default)},
				new Integer[] {ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED});
		
		registerListPreference(osmandSettings.POSITION_ON_MAP, screen,
				new String[] {getString(R.string.position_on_map_center), getString(R.string.position_on_map_bottom)},
				new Integer[] {OsmandSettings.CENTER_CONSTANT, OsmandSettings.BOTTOM_CONSTANT});
		
		entries = new String[DayNightMode.values().length];
		for(int i=0; i<entries.length; i++){
			entries[i] = DayNightMode.values()[i].toHumanString(this);
		}
		registerListPreference(osmandSettings.DAYNIGHT_MODE, screen, entries,DayNightMode.values());
		
		entries = new String[MetricsConstants.values().length];
		for(int i=0; i<entries.length; i++){
			entries[i] = MetricsConstants.values()[i].toHumanString(this);
		}
		registerListPreference(osmandSettings.METRIC_SYSTEM, screen, entries, MetricsConstants.values());
		
		//getResources().getAssets().getLocales();
		entrieValues = new String[] { "", "en", "cs", "de", "es", "fr", "hu", "it", "pt", "ru", "sk" };
		entries = new String[entrieValues.length];
		entries[0] = getString(R.string.system_locale);
		for (int i = 1; i < entries.length; i++) {
			entries[i] = entrieValues[i];
		}
		registerListPreference(osmandSettings.PREFERRED_LOCALE, screen, entries, entrieValues);
		
		Set<String> voiceFiles = getVoiceFiles();
		entries = new String[voiceFiles.size() + 1];
		entrieValues = new String[voiceFiles.size() + 1];
		int k = 0;
		entries[k++] = getString(R.string.voice_not_use);
		for (String s : voiceFiles) {
			entries[k] = s;
			entrieValues[k] = s;
			k++;
		}
		registerListPreference(osmandSettings.VOICE_PROVIDER, screen, entries, entrieValues);
		
		int startZoom = 12;
		int endZoom = 19;
		entries = new String[endZoom - startZoom + 1];
		Integer[] intValues = new Integer[endZoom - startZoom + 1];
		for (int i = startZoom; i <= endZoom; i++) {
			entries[i - startZoom] = i + ""; //$NON-NLS-1$
			intValues[i - startZoom] = i ;
		}
		registerListPreference(osmandSettings.MAX_LEVEL_TO_DOWNLOAD_TILE, screen, entries, intValues);
		
		startZoom = 3;
		endZoom = 18;
		entries = new String[endZoom - startZoom + 1];
		intValues = new Integer[endZoom - startZoom + 1];
		for (int i = startZoom; i <= endZoom; i++) {
			entries[i - startZoom] = i + ""; //$NON-NLS-1$
			intValues[i - startZoom] = i ;
		}
		registerListPreference(osmandSettings.LEVEL_TO_SWITCH_VECTOR_RASTER, screen, entries, intValues);
		
		entries = new String[RouteService.values().length];
		for(int i=0; i<entries.length; i++){
			entries[i] = RouteService.values()[i].getName();
		}
		registerListPreference(osmandSettings.ROUTER_SERVICE, screen, entries, RouteService.values());
		
		
		entries = new String[]{getString(R.string.gps_provider), getString(R.string.network_provider)};
		entrieValues = new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER};
		registerListPreference(osmandSettings.SERVICE_OFF_PROVIDER, screen, entries, entrieValues);
		
		registerTimeListPreference(osmandSettings.SAVE_TRACK_INTERVAL, screen, new int[]{1, 2, 3, 5, 10, 15, 20, 30}, new int[]{1, 2, 3, 5}, 1);
		registerTimeListPreference(osmandSettings.SERVICE_OFF_INTERVAL, screen, 
				new int[]{0, 30, 45, 60}, new int[]{2, 3, 5, 8, 10, 15, 20, 30, 40, 50, 70, 90}, 1000);
		registerTimeListPreference(osmandSettings.SERVICE_OFF_WAIT_INTERVAL, screen, 
				new int[]{15, 30, 45, 60, 90}, new int[]{2, 3, 5, 10}, 1000);
		
		
		entries = new String[ApplicationMode.values().length];
		for(int i=0; i<entries.length; i++){
			entries[i] = ApplicationMode.toHumanString(ApplicationMode.values()[i], this);
		}
		registerListPreference(osmandSettings.APPLICATION_MODE, screen, entries, ApplicationMode.values());
		
		Collection<String> rendererNames = getMyApplication().getRendererRegistry().getRendererNames();
		entries = (String[]) rendererNames.toArray(new String[rendererNames.size()]);
		registerListPreference(osmandSettings.RENDERER, screen, entries, entries);
		
		tileSourcePreference = (ListPreference) screen.findPreference(OsmandSettings.MAP_TILE_SOURCES);
		tileSourcePreference.setOnPreferenceChangeListener(this);
		

		reloadIndexes =(Preference) screen.findPreference(OsmandSettings.RELOAD_INDEXES);
		reloadIndexes.setOnPreferenceClickListener(this);
		downloadIndexes =(Preference) screen.findPreference(OsmandSettings.DOWNLOAD_INDEXES);
		downloadIndexes.setOnPreferenceClickListener(this);
		saveCurrentTrack =(Preference) screen.findPreference(OsmandSettings.SAVE_CURRENT_TRACK);
		saveCurrentTrack.setOnPreferenceClickListener(this);
		routeServiceEnabled =(CheckBoxPreference) screen.findPreference(OsmandSettings.SERVICE_OFF_ENABLED);
		routeServiceEnabled.setOnPreferenceChangeListener(this);
		applicationDir = (EditTextPreference) screen.findPreference(OsmandSettings.EXTERNAL_STORAGE_DIR);
		applicationDir.setOnPreferenceChangeListener(this);
		
		
		broadcastReceiver = new BroadcastReceiver(){
			@Override
			public void onReceive(Context context, Intent intent) {
				routeServiceEnabled.setChecked(false);
			}
			
		};
		registerReceiver(broadcastReceiver, new IntentFilter(NavigationService.OSMAND_STOP_SERVICE_ACTION));
    }

	private void updateApplicationDirTextAndSummary() {
		String storageDir = osmandSettings.getExternalStorageDirectory().getAbsolutePath();
		applicationDir.setText(storageDir);
		applicationDir.setSummary(storageDir);
	}
    
    @Override
    protected void onResume() {
		super.onResume();
		updateAllSettings();
	}
    
    @Override
    protected void onDestroy() {
    	super.onDestroy();
    	unregisterReceiver(broadcastReceiver);
    }
    
    
    public void updateAllSettings(){
    	for(OsmandPreference<Boolean> b : booleanPreferences.values()){
    		CheckBoxPreference pref = (CheckBoxPreference) screenPreferences.get(b.getId());
    		pref.setChecked(b.get());
    	}
    	
    	for(OsmandPreference<?> p : listPreferences.values()){
    		ListPreference listPref = (ListPreference) screenPreferences.get(p.getId());
    		Map<String, ?> prefValues = listPrefValues.get(p.getId());
    		String[] entryValues = new String[prefValues.size()];
    		String[] entries = new String[prefValues.size()];
    		int i = 0;
    		for(Entry<String, ?> e : prefValues.entrySet()){
    			entries[i] = e.getKey();
				entryValues[i] = e.getValue() + ""; // case of null
    			i++;
    		}
    		listPref.setEntries(entries);
    		listPref.setEntryValues(entryValues);
			listPref.setValue(p.get() + "");
    	}
    	
    	for(OsmandPreference<String> s : editTextPreferences.values()){
    		EditTextPreference pref = (EditTextPreference) screenPreferences.get(s.getId());
    		pref.setText(s.get());
    	}
    	
    	// Specific properties
		routeServiceEnabled.setChecked(getMyApplication().getNavigationService() != null);
		
		Map<String, String> entriesMap = osmandSettings.getTileSourceEntries();
		String[] entries = new String[entriesMap.size() + 1];
		String[] values = new String[entriesMap.size() + 1];
		values[0] = VECTOR_MAP;
		entries[0] = getString(R.string.vector_data);
		int ki = 1;
		for(Map.Entry<String, String> es : entriesMap.entrySet()){
			entries[ki] = es.getValue();
			values[ki] = es.getKey();
			ki++;
		}
		String value = osmandSettings.isUsingMapVectorData()? VECTOR_MAP : osmandSettings.getMapTileSourceName();
		fill(tileSourcePreference, entries, values, value);

		updateTileSourceSummary();
		
		updateApplicationDirTextAndSummary();
    }

	private void updateTileSourceSummary() {
		String mapName = " " + (osmandSettings.isUsingMapVectorData() ? getString(R.string.vector_data) : //$NON-NLS-1$
				osmandSettings.getMapTileSourceName());
		String summary = tileSourcePreference.getSummary().toString();
		if (summary.lastIndexOf(':') != -1) {
			summary = summary.substring(0, summary.lastIndexOf(':') + 1);
		}
		tileSourcePreference.setSummary(summary + mapName);
	}

  
	private void fill(ListPreference component, String[] list, String[] values, String selected) {
		component.setEntries(list);
		component.setEntryValues(values);
		component.setValue(selected);
	}
    
    
	@SuppressWarnings("unchecked")
	@Override
	public boolean onPreferenceChange(Preference preference, Object newValue) {
		// handle boolean prefences
		OsmandPreference<Boolean> boolPref = booleanPreferences.get(preference.getKey());
		OsmandPreference<Object> listPref = (OsmandPreference<Object>) listPreferences.get(preference.getKey());
		OsmandPreference<String> editPref = editTextPreferences.get(preference.getKey());
		if(boolPref != null){
			boolPref.set((Boolean)newValue);
		} else if (editPref != null) {
			editPref.set((String) newValue);
		} else if (listPref != null) {
			int ind = ((ListPreference) preference).findIndexOfValue((String) newValue);
			CharSequence entry = ((ListPreference) preference).getEntries()[ind];
			Map<String, ?> map = listPrefValues.get(preference.getKey());
			Object obj = map.get(entry);
			boolean changed = listPref.set(obj);
			
			// Specific actions after list preference changed
			if (changed) {
				if (listPref.getId().equals(osmandSettings.VOICE_PROVIDER.getId())) {
					getMyApplication().showDialogInitializingCommandPlayer(this);
				} else if (listPref.getId().equals(osmandSettings.APPLICATION_MODE.getId())) {
					updateAllSettings();
				} else if (listPref.getId().equals(osmandSettings.PREFERRED_LOCALE.getId())) {
					// restart application to update locale
					getMyApplication().checkPrefferedLocale();
					Intent intent = getIntent();
					finish();
					startActivity(intent);
				}
			}
			if (listPref.getId().equals(osmandSettings.RENDERER.getId())) {
				if(changed){
					Toast.makeText(this, R.string.renderer_load_sucess, Toast.LENGTH_SHORT).show();
				} else {
					Toast.makeText(this, R.string.renderer_load_exception, Toast.LENGTH_SHORT).show();
				}
			}
		} else if(preference == applicationDir){
			warnAboutChangingStorage((String) newValue);
			return false;
		} else if (preference == routeServiceEnabled) {
			Intent serviceIntent = new Intent(this, NavigationService.class);
			if ((Boolean) newValue) {
				ComponentName name = startService(serviceIntent);
				if (name == null) {
					routeServiceEnabled.setChecked(getMyApplication().getNavigationService() != null);
				}
			} else {
				if(!stopService(serviceIntent)){
					routeServiceEnabled.setChecked(getMyApplication().getNavigationService() != null);
				}
			}
		} else if (preference == tileSourcePreference) {
			if(VECTOR_MAP.equals((String) newValue)){
				osmandSettings.setUsingMapVectorData(true);
			} else {
				osmandSettings.setUsingMapVectorData(false);
				osmandSettings.setMapTileSource((String) newValue);
			}
			updateTileSourceSummary();
		}
		return true;
	}

	private void warnAboutChangingStorage(final String newValue) {
		final String newDir = newValue != null ? newValue.trim(): newValue;
		File path = new File(newDir);
		path.mkdirs();
		if(!path.canRead() || !path.exists()){
			Toast.makeText(this, R.string.specified_dir_doesnt_exist, Toast.LENGTH_LONG).show()	;
			return;
		}
		
		Builder builder = new AlertDialog.Builder(this);
		builder.setMessage(getString(R.string.application_dir_change_warning));
		builder.setPositiveButton(R.string.default_buttons_yes, new OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				//edit the preference
				osmandSettings.setExternalStorageDirectory(newDir);
				getMyApplication().getResourceManager().resetStoreDirectory();
				reloadIndexes();
				updateApplicationDirTextAndSummary();
			}
		});
		builder.setNegativeButton(R.string.default_buttons_cancel, null);
		builder.show();
	}

	public void reloadIndexes(){
		progressDlg = ProgressDialog.show(this, getString(R.string.loading_data), getString(R.string.reading_indexes), true);
		final ProgressDialogImplementation impl = new ProgressDialogImplementation(progressDlg);
		impl.setRunnable("Initializing app", new Runnable(){ //$NON-NLS-1$
			@Override
			public void run() {
				try {
					showWarnings(getMyApplication().getResourceManager().reloadIndexes(impl));
				} finally {
					if(progressDlg !=null){
						progressDlg.dismiss();
						progressDlg = null;
					}
				}
			}
		});
		impl.run();
	}
	
	private OsmandApplication getMyApplication() {
		return (OsmandApplication)getApplication();
	}
	
	@Override
	protected void onStop() {
		if(progressDlg !=null){
			progressDlg.dismiss();
			progressDlg = null;
		}
		super.onStop();
	}
	protected void showWarnings(List<String> warnings) {
		if (!warnings.isEmpty()) {
			final StringBuilder b = new StringBuilder();
			boolean f = true;
			for (String w : warnings) {
				if(f){
					f = false;
				} else {
					b.append('\n');
				}
				b.append(w);
			}
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					Toast.makeText(SettingsActivity.this, b.toString(), Toast.LENGTH_LONG).show();

				}
			});
		}
	}
		
	
	@Override
	public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,
			Preference preference) {
		if (preference == applicationDir) {
			return true;
		}
		return super.onPreferenceTreeClick(preferenceScreen, preference);
	}

	@Override
	public boolean onPreferenceClick(Preference preference) {
		if(preference == downloadIndexes){
			startActivity(new Intent(this, DownloadIndexActivity.class));
			return true;
		} else if(preference == reloadIndexes){
			reloadIndexes();
			return true;
		} else if(preference == saveCurrentTrack){
			SavingTrackHelper helper = new SavingTrackHelper(this);
			if (helper.hasDataToSave()) {
				progressDlg = ProgressDialog.show(this, getString(R.string.saving_gpx_tracks), getString(R.string.saving_gpx_tracks), true);
				final ProgressDialogImplementation impl = new ProgressDialogImplementation(progressDlg);
				impl.setRunnable("SavingGPX", new Runnable() { //$NON-NLS-1$
					@Override
					public void run() {
							try {
								SavingTrackHelper helper = new SavingTrackHelper(SettingsActivity.this);
								helper.saveDataToGpx();
								helper.close();
							} finally {
								if (progressDlg != null) {
									progressDlg.dismiss();
									progressDlg = null;
								}
							}
						}
					});
				impl.run();
			} else {
				helper.close();
			}
			return true;
		}
		return false;
	}
}
