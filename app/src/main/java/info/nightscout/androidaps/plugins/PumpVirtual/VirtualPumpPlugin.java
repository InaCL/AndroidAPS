package info.nightscout.androidaps.plugins.PumpVirtual;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import info.nightscout.androidaps.BuildConfig;
import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.ExtendedBolus;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PumpDescription;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.interfaces.TreatmentsInterface;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.plugins.Overview.events.EventOverviewBolusProgress;
import info.nightscout.androidaps.plugins.PumpVirtual.events.EventVirtualPumpUpdateGui;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.NSUpload;
import info.nightscout.utils.SP;

/**
 * Created by mike on 05.08.2016.
 */
public class VirtualPumpPlugin implements PluginBase, PumpInterface {
    private static Logger log = LoggerFactory.getLogger(VirtualPumpPlugin.class);

    public static Double defaultBasalValue = 0.2d;

    public static Integer batteryPercent = 50;
    public static Integer reservoirInUnits = 50;

    Date lastDataTime = new Date(0);

    boolean fragmentEnabled = true;
    boolean fragmentVisible = true;

    private static boolean fromNSAreCommingFakedExtendedBoluses = false;

    PumpDescription pumpDescription = new PumpDescription();

    static void loadFakingStatus() {
        fromNSAreCommingFakedExtendedBoluses = SP.getBoolean("fromNSAreCommingFakedExtendedBoluses", false);
    }

    public static void setFakingStatus(boolean newStatus) {
        fromNSAreCommingFakedExtendedBoluses = newStatus;
        SP.putBoolean("fromNSAreCommingFakedExtendedBoluses", fromNSAreCommingFakedExtendedBoluses);
    }

    public static boolean getFakingStatus() {
        return fromNSAreCommingFakedExtendedBoluses;
    }

    static VirtualPumpPlugin instance = null;
    public static VirtualPumpPlugin getInstance() {
        loadFakingStatus();
        if (instance == null)
            instance = new VirtualPumpPlugin();
        return instance;
    }

    public VirtualPumpPlugin() {
        pumpDescription.isBolusCapable = true;
        pumpDescription.bolusStep = 0.1d;

        pumpDescription.isExtendedBolusCapable = true;
        pumpDescription.extendedBolusStep = 0.05d;
        pumpDescription.extendedBolusDurationStep = 30;
        pumpDescription.extendedBolusMaxDuration = 8 * 60;

        pumpDescription.isTempBasalCapable = true;
        pumpDescription.tempBasalStyle = PumpDescription.PERCENT | PumpDescription.ABSOLUTE;

        pumpDescription.maxTempPercent = 500;
        pumpDescription.tempPercentStep = 10;

        pumpDescription.tempDurationStep = 30;
        pumpDescription.tempMaxDuration = 24 * 60;


        pumpDescription.isSetBasalProfileCapable = true;
        pumpDescription.basalStep = 0.01d;
        pumpDescription.basalMinimumRate = 0.01d;

        pumpDescription.isRefillingCapable = false;
    }

    @Override
    public String getFragmentClass() {
        return VirtualPumpFragment.class.getName();
    }

    @Override
    public String getName() {
        return MainApp.instance().getString(R.string.virtualpump);
    }

    @Override
    public String getNameShort() {
        String name = MainApp.sResources.getString(R.string.virtualpump_shortname);
        if (!name.trim().isEmpty()) {
            //only if translation exists
            return name;
        }
        // use long name as fallback
        return getName();
    }

    @Override
    public boolean isEnabled(int type) {
        return type == PUMP && fragmentEnabled;
    }

    @Override
    public boolean isVisibleInTabs(int type) {
        return type == PUMP && fragmentVisible;
    }

    @Override
    public boolean canBeHidden(int type) {
        return true;
    }

    @Override
    public boolean hasFragment() {
        return true;
    }

    @Override
    public boolean showInList(int type) {
        return true;
    }

    @Override
    public void setFragmentEnabled(int type, boolean fragmentEnabled) {
        if (type == PUMP) this.fragmentEnabled = fragmentEnabled;
    }

    @Override
    public void setFragmentVisible(int type, boolean fragmentVisible) {
        if (type == PUMP) this.fragmentVisible = fragmentVisible;
    }

    @Override
    public int getType() {
        return PluginBase.PUMP;
    }

    @Override
    public boolean isFakingTempsByExtendedBoluses() {
        return Config.NSCLIENT && fromNSAreCommingFakedExtendedBoluses;
    }

    @Override
    public boolean isInitialized() {
        return true;
    }

    @Override
    public boolean isSuspended() {
        return false;
    }

    @Override
    public boolean isBusy() {
        return false;
    }

    @Override
    public int setNewBasalProfile(Profile profile) {
        // Do nothing here. we are using MainApp.getConfigBuilder().getActiveProfile().getProfile();
        lastDataTime = new Date();
        return SUCCESS;
    }

    @Override
    public boolean isThisProfileSet(Profile profile) {
        return false;
    }

    @Override
    public Date lastDataTime() {
        return lastDataTime;
    }

    @Override
    public void refreshDataFromPump(String reason) {
        if (!BuildConfig.NSCLIENTOLNY)
            NSUpload.uploadDeviceStatus();
        lastDataTime = new Date();
    }

    @Override
    public double getBaseBasalRate() {
        Profile profile = MainApp.getConfigBuilder().getProfile();
        if (profile != null)
            return profile.getBasal();
        else
            return 0d;
    }

    @Override
    public PumpEnactResult deliverTreatment(DetailedBolusInfo detailedBolusInfo) {
        PumpEnactResult result = new PumpEnactResult();
        result.success = true;
        result.bolusDelivered = detailedBolusInfo.insulin;
        result.carbsDelivered = detailedBolusInfo.carbs;
        result.comment = MainApp.instance().getString(R.string.virtualpump_resultok);

        Double delivering = 0d;

        while (delivering < detailedBolusInfo.insulin) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
            }
            EventOverviewBolusProgress bolusingEvent = EventOverviewBolusProgress.getInstance();
            bolusingEvent.status = String.format(MainApp.sResources.getString(R.string.bolusdelivering), delivering);
            bolusingEvent.percent = Math.min((int) (delivering / detailedBolusInfo.insulin * 100), 100);
            MainApp.bus().post(bolusingEvent);
            delivering += 0.1d;
        }
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
        }
        EventOverviewBolusProgress bolusingEvent = EventOverviewBolusProgress.getInstance();
        bolusingEvent.status = String.format(MainApp.sResources.getString(R.string.bolusdelivered), detailedBolusInfo.insulin);
        bolusingEvent.percent = 100;
        MainApp.bus().post(bolusingEvent);
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        if (Config.logPumpComm)
            log.debug("Delivering treatment insulin: " + detailedBolusInfo.insulin + "U carbs: " + detailedBolusInfo.carbs + "g " + result);
        MainApp.bus().post(new EventVirtualPumpUpdateGui());
        lastDataTime = new Date();
        MainApp.getConfigBuilder().addToHistoryTreatment(detailedBolusInfo);
        return result;
    }

    @Override
    public void stopBolusDelivering() {

    }

    @Override
    public PumpEnactResult setTempBasalAbsolute(Double absoluteRate, Integer durationInMinutes, boolean force) {
        TreatmentsInterface treatmentsInterface = MainApp.getConfigBuilder();
        TemporaryBasal tempBasal = new TemporaryBasal();
        tempBasal.date = System.currentTimeMillis();
        tempBasal.isAbsolute = true;
        tempBasal.absoluteRate = absoluteRate;
        tempBasal.durationInMinutes = durationInMinutes;
        tempBasal.source = Source.USER;
        PumpEnactResult result = new PumpEnactResult();
        result.success = true;
        result.enacted = true;
        result.isTempCancel = false;
        result.absolute = absoluteRate;
        result.duration = durationInMinutes;
        result.comment = MainApp.instance().getString(R.string.virtualpump_resultok);
        treatmentsInterface.addToHistoryTempBasal(tempBasal);
        if (Config.logPumpComm)
            log.debug("Setting temp basal absolute: " + result);
        MainApp.bus().post(new EventVirtualPumpUpdateGui());
        lastDataTime = new Date();
        return result;
    }

    @Override
    public PumpEnactResult setTempBasalPercent(Integer percent, Integer durationInMinutes) {
        TreatmentsInterface treatmentsInterface = MainApp.getConfigBuilder();
        PumpEnactResult result = new PumpEnactResult();
        if (MainApp.getConfigBuilder().isTempBasalInProgress()) {
            result = cancelTempBasal(false);
            if (!result.success)
                return result;
        }
        TemporaryBasal tempBasal = new TemporaryBasal();
        tempBasal.date = System.currentTimeMillis();
        tempBasal.isAbsolute = false;
        tempBasal.percentRate = percent;
        tempBasal.durationInMinutes = durationInMinutes;
        tempBasal.source = Source.USER;
        result.success = true;
        result.enacted = true;
        result.percent = percent;
        result.isPercent = true;
        result.isTempCancel = false;
        result.duration = durationInMinutes;
        result.comment = MainApp.instance().getString(R.string.virtualpump_resultok);
        treatmentsInterface.addToHistoryTempBasal(tempBasal);
        if (Config.logPumpComm)
            log.debug("Settings temp basal percent: " + result);
        MainApp.bus().post(new EventVirtualPumpUpdateGui());
        lastDataTime = new Date();
        return result;
    }

    @Override
    public PumpEnactResult setExtendedBolus(Double insulin, Integer durationInMinutes) {
        TreatmentsInterface treatmentsInterface = MainApp.getConfigBuilder();
        PumpEnactResult result = cancelExtendedBolus();
        if (!result.success)
            return result;
        ExtendedBolus extendedBolus = new ExtendedBolus();
        extendedBolus.date = System.currentTimeMillis();
        extendedBolus.insulin = insulin;
        extendedBolus.durationInMinutes = durationInMinutes;
        extendedBolus.source = Source.USER;
        result.success = true;
        result.enacted = true;
        result.bolusDelivered = insulin;
        result.isTempCancel = false;
        result.duration = durationInMinutes;
        result.comment = MainApp.instance().getString(R.string.virtualpump_resultok);
        treatmentsInterface.addToHistoryExtendedBolus(extendedBolus);
        if (Config.logPumpComm)
            log.debug("Setting extended bolus: " + result);
        MainApp.bus().post(new EventVirtualPumpUpdateGui());
        lastDataTime = new Date();
        return result;
    }

    @Override
    public PumpEnactResult cancelTempBasal(boolean force) {
        TreatmentsInterface treatmentsInterface = MainApp.getConfigBuilder();
        PumpEnactResult result = new PumpEnactResult();
        result.success = true;
        result.isTempCancel = true;
        result.comment = MainApp.instance().getString(R.string.virtualpump_resultok);
        if (treatmentsInterface.isTempBasalInProgress()) {
            result.enacted = true;
            TemporaryBasal tempStop = new TemporaryBasal(System.currentTimeMillis());
            tempStop.source = Source.USER;
            treatmentsInterface.addToHistoryTempBasal(tempStop);
            //tempBasal = null;
            if (Config.logPumpComm)
                log.debug("Canceling temp basal: " + result);
            MainApp.bus().post(new EventVirtualPumpUpdateGui());
        }
        lastDataTime = new Date();
        return result;
    }

    @Override
    public PumpEnactResult cancelExtendedBolus() {
        TreatmentsInterface treatmentsInterface = MainApp.getConfigBuilder();
        PumpEnactResult result = new PumpEnactResult();
        if (treatmentsInterface.isInHistoryExtendedBoluslInProgress()) {
            ExtendedBolus exStop = new ExtendedBolus(System.currentTimeMillis());
            exStop.source = Source.USER;
            treatmentsInterface.addToHistoryExtendedBolus(exStop);
        }
        result.success = true;
        result.enacted = true;
        result.isTempCancel = true;
        result.comment = MainApp.instance().getString(R.string.virtualpump_resultok);
        if (Config.logPumpComm)
            log.debug("Canceling extended basal: " + result);
        MainApp.bus().post(new EventVirtualPumpUpdateGui());
        lastDataTime = new Date();
        return result;
    }

    @Override
    public JSONObject getJSONStatus() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
        if (!preferences.getBoolean("virtualpump_uploadstatus", false)) {
            return null;
        }
        JSONObject pump = new JSONObject();
        JSONObject battery = new JSONObject();
        JSONObject status = new JSONObject();
        JSONObject extended = new JSONObject();
        try {
            battery.put("percent", batteryPercent);
            status.put("status", "normal");
            extended.put("Version", BuildConfig.VERSION_NAME + "-" + BuildConfig.BUILDVERSION);
            try {
                extended.put("ActiveProfile", MainApp.getConfigBuilder().getProfileName());
            } catch (Exception e) {
            }
            TemporaryBasal tb = MainApp.getConfigBuilder().getTempBasalFromHistory(System.currentTimeMillis());
            if (tb != null) {
                extended.put("TempBasalAbsoluteRate", tb.tempBasalConvertedToAbsolute(System.currentTimeMillis()));
                extended.put("TempBasalStart", DateUtil.dateAndTimeString(tb.date));
                extended.put("TempBasalRemaining", tb.getPlannedRemainingMinutes());
            }
            ExtendedBolus eb = MainApp.getConfigBuilder().getExtendedBolusFromHistory(System.currentTimeMillis());
            if (eb != null) {
                extended.put("ExtendedBolusAbsoluteRate", eb.absoluteRate());
                extended.put("ExtendedBolusStart", DateUtil.dateAndTimeString(eb.date));
                extended.put("ExtendedBolusRemaining", eb.getPlannedRemainingMinutes());
            }
            status.put("timestamp", DateUtil.toISOString(new Date()));

            pump.put("battery", battery);
            pump.put("status", status);
            pump.put("extended", extended);
            pump.put("reservoir", reservoirInUnits);
            pump.put("clock", DateUtil.toISOString(new Date()));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return pump;
    }

    @Override
    public String deviceID() {
        return "VirtualPump";
    }

    @Override
    public PumpDescription getPumpDescription() {
        return pumpDescription;
    }

    @Override
    public String shortStatus(boolean veryShort) {
        return "Virtual Pump";
    }

}
