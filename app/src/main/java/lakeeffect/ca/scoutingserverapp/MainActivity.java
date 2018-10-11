package lakeeffect.ca.scoutingserverapp;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    BluetoothAdapter bluetoothAdapter;

    ArrayList<String> deviceNames = new ArrayList<>();

    Button connect;
    Button visibility;
    TextView status;
    EditText versionNumEditText;
    EditText timeOffEditText;
    Button timeOffSet;
    EditText timeOffMatchNumEditText;

    int minVersionNum;
    TimeOff targetTimeOff;

    String labels = null; //Retrieved until both are set
    String qualitativeLabels = null;

    ArrayList<String> uuids = new ArrayList<>();

    ArrayList<PullDataThread> pullDataThreads = new ArrayList<>();

    //data for the devices added to the list
    ArrayList<BluetoothDevice> devicesSelected = new ArrayList<>();
    ArrayList<View> devicesSelectedView = new ArrayList<>();

    //The format for time when displayed
    SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm");
    Calendar cal = Calendar.getInstance();

    //the robots schedules
    ArrayList<ArrayList<Integer>> robotSchedule = new ArrayList<>();

    //list of allNames added
    ArrayList<String> allNames = new ArrayList<>();
    //the names that have been checked off
    ArrayList<Scout> selectedNames = new ArrayList<>();
    //for each selected name, there is an array of assigned robots (0 - 5 per match, -1 being a break)
    ArrayList<int[]> assignedRobot = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        Intent turnOn = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(turnOn, 0);

        connect = ((Button) findViewById(R.id.connect));
        connect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openPullDataDialog();
            }
        });

        visibility = ((Button) findViewById(R.id.visibility));
        visibility.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
                intent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 0);
                startActivityForResult(intent, 3);
            }
        });

        status = ((TextView) findViewById(R.id.status));
        versionNumEditText = ((EditText) findViewById(R.id.versionNum));
        versionNumEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                if (versionNumEditText.getText().toString().equals("")) return;

                minVersionNum = Integer.parseInt(versionNumEditText.getText().toString());

                SharedPreferences sharedPreferences = getSharedPreferences("minVersionNum", MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putInt("minVersionNum", minVersionNum);
                editor.apply();
            }
        });
        SharedPreferences sharedPreferences = getSharedPreferences("minVersionNum", MODE_PRIVATE);
        minVersionNum = sharedPreferences.getInt("minVersionNum", 0);
        versionNumEditText.setText(minVersionNum + "");

        //load schedule into memory
        try {
            readSchedule();
        } catch (IOException e) {
            e.printStackTrace();
        }

        //load schedule when button is pressed.
        findViewById(R.id.reloadSchedule).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    readSchedule();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        //setup time off text view
        timeOffEditText = ((EditText) findViewById(R.id.timeOff));
        timeOffMatchNumEditText = ((EditText) findViewById(R.id.timeOffMatchNum));
        timeOffSet = ((Button) findViewById(R.id.timeOffSetButton));
        timeOffSet.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if (timeOffEditText.getText().toString().equals("")) return;
                if (timeOffMatchNumEditText.getText().toString().equals("")){
                    runOnUiThread(new Thread(){
                        public void run() {
                            Toast.makeText(MainActivity.this, "You need to specify a match number for this to happen at (can be 1)", Toast.LENGTH_SHORT).show();
                        }
                    });
                    return;
                }

                int newTimeOff = Integer.parseInt(timeOffEditText.getText().toString());
                int switchMatch = Integer.parseInt(timeOffMatchNumEditText.getText().toString()) - 1;

                targetTimeOff.targetTimeOff.add(newTimeOff);
                targetTimeOff.switchMatches.add(switchMatch);

                SharedPreferences sharedPreferences = getSharedPreferences("targetTimeOff", MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putInt("targetTimeOff" + (targetTimeOff.targetTimeOff.size() - 1), newTimeOff);
                editor.putInt("switchMatches" + (targetTimeOff.switchMatches.size() - 1), switchMatch);
                editor.putInt("targetTimeOffSize" + (targetTimeOff.targetTimeOff.size()), targetTimeOff.targetTimeOff.size());
                editor.putInt("switchMatchesSize" + (targetTimeOff.switchMatches.size()), targetTimeOff.switchMatches.size());
                editor.apply();

                runOnUiThread(new Thread(){
                    public void run() {
                        Toast.makeText(MainActivity.this, "Added new time off", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
        sharedPreferences = getSharedPreferences("targetTimeOff", MODE_PRIVATE);
        targetTimeOff = new TimeOff();
        int targetTimeOffSize = sharedPreferences.getInt("targetTimeOffSize", 1);
        for (int i = 0; i < targetTimeOffSize; i++) {
            targetTimeOff.targetTimeOff.add(sharedPreferences.getInt("targetTimeOff" + i, 2));
        }
        int switchMatchesSize = sharedPreferences.getInt("switchMatchesSize", 1);
        for (int i = 0; i < switchMatchesSize; i++) {
            targetTimeOff.switchMatches.add(sharedPreferences.getInt("switchMatches" + i, 0));
        }

        //set text view to current target time off
        timeOffEditText.setText(targetTimeOff.getTimeOff(robotSchedule.size() - 1) + "");

        //add click listener for pull all
        findViewById(R.id.pullAll).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                for (int i = 0; i < devicesSelected.size(); i++){
                    pullFromDevice(devicesSelected.get(i), devicesSelectedView.get(i));
                }
            }
        });

        //add click listener for add user
        findViewById(R.id.addUser).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openNameEditor();
            }
        });

        //load UUIDs of all data collected to make sure there are no duplicates
        ArrayList<String> data = new ArrayList<>();
        try {
            data = readData();
        } catch (IOException e) {
            e.printStackTrace();
        }

        for(String line: data){
            uuids.add(getUUIDFromData(line));
        }

        //load names
        SharedPreferences namesPreferences = getSharedPreferences("names", MODE_PRIVATE);
        String allNamesText = namesPreferences.getString("allNames", "");
        if (!allNamesText.equals("")) {
            allNames = new ArrayList<>(Arrays.asList(allNamesText.split(",")));
        }

        String selectedNamesText = namesPreferences.getString("selectedNames", "");
        if (!selectedNamesText.equals("")) {
            String[] selectedNamesArray = selectedNamesText.split(",");

            for (int i = 0; i < selectedNamesArray.length; i++) {
                Scout scout = new Scout(selectedNamesArray[i]);
                selectedNames.add(scout);
            }
        }

        String selectedNameStartMatchesText = namesPreferences.getString("selectedNameStartMatches", "");
        if (!selectedNameStartMatchesText.equals("")) {
            String[] selectedNameStartMatchesArray = selectedNameStartMatchesText.split(",");

            for (int i = 0; i < selectedNameStartMatchesArray.length; i++) {
                //add all start matches
                String[] scoutStartMatchesArray = selectedNameStartMatchesArray[i].split(";");
                for (int s = 0; s < scoutStartMatchesArray.length; s++) {
                    selectedNames.get(i).startMatches.add(Integer.parseInt(scoutStartMatchesArray[s]));
                }
            }
        }

        String selectedNameLastMatchesText = namesPreferences.getString("selectedNameLastMatches", "");
        if (!selectedNameLastMatchesText.equals("")) {
            String[] selectedNameLastMatchesArray = selectedNameLastMatchesText.split(",");

            for (int i = 0; i < selectedNameLastMatchesArray.length; i++) {
                if (!selectedNameLastMatchesArray[i].equals("")) {
                    //add all last matches
                    String[] scoutLastMatchesArray = selectedNameLastMatchesArray[i].split(";");
                    for (int s = 0; s < scoutLastMatchesArray.length; s++) {
                        selectedNames.get(i).lastMatches.add(Integer.parseInt(scoutLastMatchesArray[s]));
                    }
                }
            }
        }

        //load devices
        SharedPreferences devicesPreferences = getSharedPreferences("devices", MODE_PRIVATE);
        String deviceText = devicesPreferences.getString("selectedDevices", "");
        if (!deviceText.equals("")) {
            String[] deviceArray = deviceText.split(",");

            for (int i = 0; i < deviceArray.length; i++) {
                BluetoothDevice bluetoothDevice = bluetoothAdapter.getRemoteDevice(deviceArray[i]);

                addSelectedDevice(bluetoothDevice);
            }
        }

        //create the schedule out of the currently selected names
        final String success = createSchedule();
        if (success != null) {
            runOnUiThread(new Thread(){
                public void run() {
                    Toast.makeText(MainActivity.this, success, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    /**
     * Opens the dialog that chooses which device to pull data from
     */
    public void openPullDataDialog(){
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();

        final BluetoothDevice[] devices = pairedDevices.toArray(new BluetoothDevice[0]);

        String[] names = new String[devices.length];
        if (pairedDevices.size() > 0) {
            for (int i = 0; i < devices.length; i++) {
                names[i] = devices[i].getName();
            }
        }

        new AlertDialog.Builder(MainActivity.this)
        .setTitle("Which device would you like to add?")
        .setMultiChoiceItems(names, null, null)
        .setPositiveButton("Add Devices", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                SparseBooleanArray checked = ((AlertDialog) dialog).getListView().getCheckedItemPositions();

                for (int i = 0; i < ((AlertDialog) dialog).getListView().getCount(); i++) {
                    if(checked.get(i)) {
                        addSelectedDevice(devices[i]);
                    }
                }

                //convert all devices into a csv of addresses
                String devicesString = "";

                for (int i = 0; i < devicesSelected.size(); i++){
                    //add it to the list
                    devicesString += devicesSelected.get(i).getAddress();
                    if (i != devicesSelected.size() - 1){
                        devicesString += ",";
                    }
                }

                //save this data in shared preferences
                SharedPreferences sharedPreferences = getSharedPreferences("devices", MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString("selectedDevices", devicesString);
                editor.apply();

                runOnUiThread(new Thread(){
                    public void run(){
                        Toast.makeText(MainActivity.this, "Updated list", Toast.LENGTH_LONG).show();
                    }
                });
            }


        })
        .create()
        .show();
    }

    //Add one device to the selected device list
    public void addSelectedDevice(final BluetoothDevice device) {
        devicesSelected.add(device);

        final LinearLayout linearLayout = (LinearLayout) findViewById(R.id.devicesMenuLinearLayout);

        final View newDeviceMenu = LayoutInflater.from(this).inflate(R.layout.device_name, null);

        devicesSelectedView.add(newDeviceMenu);

        //set onClick listeners
        newDeviceMenu.findViewById(R.id.pull).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pullFromDevice(device, newDeviceMenu);
            }
        });
        newDeviceMenu.findViewById(R.id.remove).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                linearLayout.removeView(newDeviceMenu);

                devicesSelected.remove(device);

                //update the shared preferences with the new list of devices
                //convert all devices into a csv of addresses
                String devicesString = "";

                for (int i = 0; i < devicesSelected.size(); i++){
                    //add it to the list
                    devicesString += devicesSelected.get(i).getAddress();
                    if (i != devicesSelected.size() - 1){
                        devicesString += ",";
                    }
                }

                //save this data in shared preferences
                SharedPreferences sharedPreferences = getSharedPreferences("devices", MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString("selectedDevices", devicesString);
                editor.apply();

                runOnUiThread(new Thread(){
                    public void run(){
                        Toast.makeText(MainActivity.this, "Removed " + device.getName(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });

        //set text
        ((TextView) newDeviceMenu.findViewById(R.id.deviceName)).setText(device.getName() + " (Never pulled)");

        linearLayout.addView(newDeviceMenu);
    }

    //Pull data from the specified device and send data about schedule
    public void pullFromDevice(BluetoothDevice device, View deviceMenu) {
        pullDataThreads.add(new PullDataThread(MainActivity.this, device, deviceMenu));

        if(pullDataThreads.size() <= 1) {
            pullDataThreads.get(0).start();
        }
    }

    //reads the schedule code and will save the robot schedule for you
    //store schedule in /#ScoutingSchedule/
    //schedule is a csv with 6 robots on each line
    public void readSchedule() throws IOException {
        File sdCard = Environment.getExternalStorageDirectory();

        File[] files = new File(sdCard.getPath() + "/#ScoutingSchedule/").listFiles();

        //there is no schedule
        if(files == null) {
            Toast.makeText(this, "There is no schedule", Toast.LENGTH_LONG).show();
            return;
        }

        for(int i = 0; i < files.length; i++) {
            if(files[i].isDirectory()) continue;

            BufferedReader br = new BufferedReader(new FileReader(files[i]));
            String line;

            int lineNum = 0;
            while ((line = br.readLine()) != null) {
                if(lineNum > 0) {
                    String[] robots = line.split(",");

                    ArrayList<Integer> robotNumbers = new ArrayList<>();

                    for (int s = 0; s < robots.length; s++) {
                        robotNumbers.add(Integer.parseInt(robots[i]));
                    }

                    robotSchedule.add(robotNumbers);
                }
                lineNum ++;
            }
            br.close();
        }
    }

    //creates the schedule based on the selected usernames
    //returns null if successful, and error message if not
    public String createSchedule() {
        if (selectedNames.size() < 6) {
            return "You must select at least 6 scouts";
        }

        //scouts currently scouting or not
        Scout[] scoutsOn = new Scout[6];
        ArrayList<Scout> scoutsOff = new ArrayList<>();

        //set assigned robots to correct size
        assignedRobot = new ArrayList<>();
        for (int i = 0; i < selectedNames.size(); i++) {
            assignedRobot.add(new int[robotSchedule.size()]);
        }

        //reset assigned robots
        for (int i = 0; i < assignedRobot.size(); i++) {
            for (int s = 0; s < assignedRobot.get(i).length; s++) {
                assignedRobot.get(i)[s] = -1;
            }
        }

        //used to determine if there are not enough ready scouts
        int nonReadyScouts = 0;
        for (int i = 0; i < scoutsOn.length; i++) {
            if (scoutsOn.length - nonReadyScouts < 6) {
                //not enough scouts
                return "You must select at least 6 ready scouts";
            }

            //if they are not starting on the first match
            if (selectedNames.get(i).getLowestStartMatch() > 0) {
                Scout scout = selectedNames.get(i);
                selectedNames.remove(scout);
                selectedNames.add(scout);
                //try again
                i--;
                //add to the non ready scouts to make sure this is not an infinite loop
                nonReadyScouts++;
                continue;
            }

            scoutsOn[i] = new Scout(i, selectedNames.get(i).name);
        }

        for (int i = 6; i < selectedNames.size(); i++) {
            //if they are not starting on the first match
            if (selectedNames.get(i).getLowestStartMatch() > 0) {
                continue;
            }

            Scout scout = new Scout(i, selectedNames.get(i).name);
            scoutsOff.add(scout);
            scout.timeOff = 0;
        }

        for (int matchNum = 0; matchNum < robotSchedule.size(); matchNum++) {
            //figure out if some selected names should be added or removed because it is now their start match or last match
            for (int i = 0; i < selectedNames.size(); i++) {
                //if it is time to add this scout to the roster and they are not already added
                Scout scout = selectedNames.get(i);
                boolean existsAtMatch = scout.existsAtMatch(matchNum);
                if (existsAtMatch && getScout(i, scoutsOff) == -1 && getScout(i, scoutsOn) == -1) {
                    Scout newScout = new Scout(i, scout.name);
                    scoutsOff.add(newScout);
                    newScout.timeOff = 0;
                } else if (!existsAtMatch && (getScout(i, scoutsOff) != -1 || getScout(i, scoutsOn) != -1)) {
                    int index = getScout(i, scoutsOff);
                    if (index == -1){
                        index = getScout(i, scoutsOn);

                        //check if there this scout can switch off
                        boolean canSwitchOff = false;
                        for (int s = 0; s < scoutsOff.size(); s++) {
                            if (matchNum - scoutsOff.get(s).timeOff >= targetTimeOff.getTimeOff(matchNum)) {
                                canSwitchOff = true;
                                break;
                            }
                        }

                        if (!canSwitchOff) {
                            return "Nobody is ready to switch off this match, try next match.";
                        }

                        //make sure this scout goes off next
                        scoutsOn[index].timeOn = -targetTimeOff.getTimeOff(matchNum);
                    } else {
                        scoutsOff.remove(index);
                    }
                }
            }

            //calculate the schedule for this match
            //find scouts to switch on (the scouts that have been off >= targetTimeOff)
            ArrayList<Scout> scoutsToSwitchOn = new ArrayList<>();
            for (int i = 0; i < scoutsOff.size(); i++) {
                if (matchNum - scoutsOff.get(i).timeOff >= targetTimeOff.getTimeOff(matchNum) && scoutsToSwitchOn.size() < 6) {
                    scoutsToSwitchOn.add(scoutsOff.get(i));
                }
            }

            //find scouts to switch off (scouts with the highest time)
            ArrayList<Scout> scoutsToSwitchOff = new ArrayList<>();
            //sort by time on
            for (int i = 0; i < scoutsOn.length; i++) {
                //the index to add this scout when sorted
                int indexToAdd = scoutsToSwitchOff.size();
                for (int s = 0; s < scoutsToSwitchOff.size(); s++) {
                    if (matchNum - scoutsOn[i].timeOn > matchNum - scoutsToSwitchOff.get(s).timeOn) {
                        indexToAdd = s;
                        break;
                    }
                }

                //add at index
                scoutsToSwitchOff.add(indexToAdd, scoutsOn[i]);
            }

            //swap scouts on with scouts off
            for (int i = 0; i < scoutsToSwitchOn.size(); i++) {
                //scout switching on and off
                Scout switchingOn = scoutsToSwitchOn.get(i);
                Scout switchingOff = scoutsToSwitchOff.get(i);

                scoutsOn[getScout(switchingOff.id, scoutsOn)] = switchingOn;

                scoutsOff.remove(switchingOn);
                scoutsOff.add(switchingOff);

                //update targetTimeOff and timeOn
                switchingOn.timeOn = matchNum;
                switchingOff.timeOff = matchNum;
            }

            //set the schedule for this match
            for (int i = 0; i < scoutsOn.length; i++) {
                assignedRobot.get(scoutsOn[i].id)[matchNum] = i;
            }
            for (int i = 0; i < scoutsOff.size(); i++) {
                assignedRobot.get(scoutsOff.get(i).id)[matchNum] = -1;
            }
        }

        return null;
    }

    public int getScout(int id, ArrayList<Scout> scouts) {
        for (int i = 0; i < scouts.size(); i++) {
            if (scouts.get(i).id == id) {
                return i;
            }
        }
        return -1;
    }

    public int getScout(int id, Scout[] scouts) {
        for (int i = 0; i < scouts.length; i++) {
            if (scouts[i].id == id) {
                return i;
            }
        }
        return -1;
    }

    public int getScout(String name, ArrayList<Scout> scouts) {
        for (int i = 0; i < scouts.size(); i++) {
            if (scouts.get(i).name.equals(name)) {
                return i;
            }
        }
        return -1;
    }

    //dialog box that lets you edit and deselect names
    public void openNameEditor() {

        final ScrollView fullScrollView = new ScrollView(this);

        //contains all the names plus the view to add more
        final LinearLayout fullView = new LinearLayout(this);
        fullView.setOrientation(LinearLayout.VERTICAL);

        //all the names already in the list
        final LinearLayout createdNames = new LinearLayout(this);
        createdNames.setOrientation(LinearLayout.VERTICAL);

        //create view to add a new name
        final View addName = View.inflate(this, R.layout.name_submitter, null);
        //get match number view
        final TextView currentMatchNumber = ((TextView) addName.findViewById(R.id.currentMatchNumber));

        //add checkbox and close button for each username
        for (int i = 0; i < allNames.size(); i++) {
            final View view = View.inflate(this, R.layout.closable_checkbox, null);

            CheckBox checkBox = ((CheckBox) view.findViewById(R.id.nameCheckBox));

            final String name = allNames.get(i);

            checkBox.setText(name);

            //if it is selected, check the box
            int index = getScout(name, selectedNames);
            if (index != -1 && selectedNames.get(index).existsAtMatch(robotSchedule.size() - 1)) {
                checkBox.setChecked(true);
            }

            //set checkbox onChange listener
            checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    nameClicked(buttonView, isChecked, name, currentMatchNumber);
                }
            });


            //set close action
            view.findViewById(R.id.nameClose).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    createdNames.removeView(view);
                    allNames.remove(name);
                    if (getScout(name, selectedNames) != -1) {
                        selectedNames.remove(getScout(name, selectedNames));
                    }

                    updateNames();
                }
            });

            createdNames.addView(view);
        }

        fullView.addView(createdNames);

        //set add name action
        addName.findViewById(R.id.nameAddButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EditText nameEditText = ((EditText) addName.findViewById(R.id.nameEditText));

                final String name = nameEditText.getText().toString();

                //reset text
                nameEditText.setText("");

                //add it to the list
                allNames.add(name);

                updateNames();

                //add it to the UI
                final View view = View.inflate(MainActivity.this, R.layout.closable_checkbox, null);

                CheckBox checkBox = ((CheckBox) view.findViewById(R.id.nameCheckBox));

                checkBox.setText(name);

                //set checkbox onChange listener
                checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        nameClicked(buttonView, isChecked, name, currentMatchNumber);
                    }
                });

                //set close action
                view.findViewById(R.id.nameClose).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        createdNames.removeView(view);
                        allNames.remove(name);
                        if (getScout(name, selectedNames) != -1) {
                            selectedNames.remove(getScout(name, selectedNames));
                        }

                        updateNames();
                    }
                });

                createdNames.addView(view);
            }
        });

        fullView.addView(addName);

        fullScrollView.addView(fullView);

        //create the dialog box
        new AlertDialog.Builder(MainActivity.this)
                .setTitle("Select Names")
                .setView(fullScrollView)
                .setPositiveButton("Ok", null)
                .show();
    }

    //called when a name is checked or unchecked
    public void nameClicked(CompoundButton checkbox, boolean isChecked, String name, TextView currentMatchNumber) {
        int scoutIndex = getScout(name, selectedNames);
        int matchNum = Integer.parseInt(currentMatchNumber.getText().toString()) - 1;

        if (isChecked) {
            if (scoutIndex == -1) {
                selectedNames.add(new Scout(name, matchNum));
            } else {
                Scout scout = selectedNames.get(scoutIndex);

                if (scout.existsAtMatch(matchNum)) {
                    //this action should not happen, this is an invalid time
                    runOnUiThread(new Thread() {
                        public void run() {
                            Toast.makeText(MainActivity.this, "This scout is already enabled at that match", Toast.LENGTH_SHORT).show();
                        }
                    });
                    checkbox.setChecked(false);
                    return;
                }

                //enable them at that match number
                scout.startMatches.add(matchNum);
            }
        } else {
            if (scoutIndex != -1) {
                Scout scout = selectedNames.get(scoutIndex);

                if (!scout.existsAtMatch(matchNum)) {
                    //this action should not happen, this is an invalid time
                    runOnUiThread(new Thread() {
                        public void run() {
                            Toast.makeText(MainActivity.this, "The scout is not enabled at that match", Toast.LENGTH_SHORT).show();
                        }
                    });
                    checkbox.setChecked(true);
                    return;
                }

                scout.lastMatches.add(matchNum);

                //check if there were errors from this change
                final String success = createSchedule();
                if (success != null) {
                    runOnUiThread(new Thread(){
                        public void run() {
                            Toast.makeText(MainActivity.this, success, Toast.LENGTH_SHORT).show();
                        }
                    });
                    //undo as an error has been caused
                    scout.lastMatches.remove(scout.lastMatches.size() - 1);
                    checkbox.setChecked(true);
                }
            }
        }

        updateNames();
    }

    //updates the shared preferences with the all names and selected names list
    public void updateNames() {
        SharedPreferences sharedPreferences = getSharedPreferences("names", MODE_PRIVATE);

        //get all the names in csv
        String names = "";
        for (String name : allNames) {
            names += name;
            if (allNames.indexOf(name) < allNames.size() - 1) {
                names += ",";
            }
        }

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("allNames", names);

        //get all the selected names in csv
        String allSelectedNames = "";
        String allSelectedNameStartMatches = "";
        String allSelectedNameLastMatches = "";
        for (Scout scout : selectedNames) {
            allSelectedNames += scout.name;
            for (int i = 0; i < scout.startMatches.size(); i++) {
                allSelectedNameStartMatches += scout.startMatches.get(i);
                if (i < scout.startMatches.size() - 1) {
                    allSelectedNameStartMatches += ";";
                }
            }
            for (int i = 0; i < scout.lastMatches.size(); i++) {
                allSelectedNameLastMatches += scout.lastMatches.get(i);
                if (i < scout.lastMatches.size() - 1) {
                    allSelectedNameLastMatches += ";";
                }
            }

            if (selectedNames.indexOf(scout) < selectedNames.size() - 1) {
                allSelectedNames += ",";
                allSelectedNameStartMatches += ",";
                allSelectedNameLastMatches += ",";
            }
        }
        editor.putString("selectedNames", allSelectedNames);
        editor.putString("selectedNameStartMatches", allSelectedNameStartMatches);
        editor.putString("selectedNameLastMatches", allSelectedNameLastMatches);


        editor.apply();


        //update schedule
        final String success = createSchedule();
        if (success != null) {
            runOnUiThread(new Thread(){
                public void run() {
                    Toast.makeText(MainActivity.this, success, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    @Override
    public void onBackPressed() {

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            for(PullDataThread pullDataThread: pullDataThreads){
                if(pullDataThread != null) pullDataThread.onDestroy();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String parse(String string){
        String[] data = string.split("\n")[1].split(","); //auto
        String message = "Available: ";
        ArrayList<Integer> available = new ArrayList<>();

        for(int i=0;i<data.length;i+=3){
            if(Boolean.parseBoolean(data[i])){
                message += getDefense(i/3) + ", ";
                available.add(new Integer(i/3));
            }
        }

        message+= "\nReached: ";
        for(int i=1;i<data.length;i+=3){
            if(Boolean.parseBoolean(data[i])){
                message += getDefense((i-1)/3) + ", ";
            }
        }

        message+= "\nCrossed: ";
        for(int i=2;i<data.length;i+=3){
            if(Boolean.parseBoolean(data[i])){
                message += getDefense((i-2)/3) + ", ";
            }
        }

        data = string.split("\n")[2].split(","); //teleop

        for(Integer i: available){
            message += "\n" + getDefense(i) + ": " + data[i*2] + " attemps and " + data[i*2+1] + " crossed";
        }

        message += "Low Goals: " + data[data.length-4] + " attemps and " + data[data.length-3] + " goals scored";
        message += "High Goals: " + data[data.length-2] + " attemps and " + data[data.length-1] + " goals scored";

            return message;
    }

    public String getDefense(int i){
        switch(i){
            case 0:
                return "Low Bar";
            case 1:
                return "Portcullis";
            case 2:
                return "Cheval de Frise";
            case 3:
                return "Moat";
            case 4:
                return "Rampart";
            case 5:
                return "Drawbridge";
            case 6:
                return "Sally Port";
            case 7:
                return "Rock Wall";
            case 8:
                return "Rough Terrain";
        }
        return "";
    }

    public String getUUIDFromData(final String data){
        String[] dataArray = data.split(",");
        if(dataArray.length <= 2){
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getApplicationContext(), "Your data is broken, the amount of commas is too small in " + data, Toast.LENGTH_LONG).show();
                }
            });
            return "null";
        }
        return dataArray[dataArray.length-2];

    }

    public ArrayList<String> readData() throws IOException {

        File sdCard = Environment.getExternalStorageDirectory();

        File file = new File(sdCard.getPath() + "/#ScoutingData/");
        file.mkdirs();

        File[] files = file.listFiles();
        if(files == null) return new ArrayList<>(); //no files in the directory

        ArrayList<String> data = new ArrayList<>();

        for(int i = 0; i < files.length; i++) {
            if(files[i].isDirectory()) continue;

            BufferedReader br = new BufferedReader(new FileReader(files[i]));
            String line;

            int lineNum = 0;
            while ((line = br.readLine()) != null) {
                if(lineNum > 0) {
                    data.add(line);
                }
                lineNum ++;
            }
            br.close();
        }

        return data;
    }

    public void saveEvents(String data) {

        if(data.split(":").length < 2){
            //there are no events
            return;
        }

        File sdCard = Environment.getExternalStorageDirectory();

        File file = new File(sdCard.getPath() + "/#ScoutingData/EventData/" + data.split(":")[0] + ".csv");

        data = data.replace(data.split(":")[0] + ":" + data.split(":")[1] + ":", "");

        if (data.equals("") || data.equals("\n")) {
            return;
        }

        if (data.endsWith("\n")) {
            data = data.substring(0, data.length() - 1);
        }

        try {
            boolean newfile = false;
            file.getParentFile().mkdirs();
            if (!file.exists()) {
                file.createNewFile();
                newfile = true;
            }

            FileOutputStream f = new FileOutputStream(file, true);

            OutputStreamWriter out = new OutputStreamWriter(f);

            out.write(data + "\n");

            out.close();
            f.close();
        }catch(IOException e){
            e.printStackTrace();
        }

    }

    public void save(String data, String labels){

        saveEvents(data);

        File sdCard = Environment.getExternalStorageDirectory();

        File file = new File(sdCard.getPath() + "/#ScoutingData/" + data.split(":")[0] + ".csv");

        data = data.replace(data.split(":")[0] + ":", "");

        if(data.split(":").length >= 2){
            //there are events
            data = data.replace(":" + data.split(":")[1], "");
        }

        System.out.println(data);

        try {
            boolean newfile = false;
            file.getParentFile().mkdirs();
            if (!file.exists()) {
                file.createNewFile();
                newfile = true;
            }

            FileOutputStream f = new FileOutputStream(file, true);

            OutputStreamWriter out = new OutputStreamWriter(f);

            if(newfile) out.write(labels);
            out.write(data);

//            runOnUiThread(new Runnable() {
//                @Override
//                public void run() {
//                    Toast.makeText(MainActivity.this, "Saved",
//                            Toast.LENGTH_LONG).show();
//                }
//            });

            out.close();
            f.close();
        }catch(IOException e){
            e.printStackTrace();
        }
    }
}
