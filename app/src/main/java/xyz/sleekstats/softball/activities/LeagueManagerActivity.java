package xyz.sleekstats.softball.activities;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.support.design.widget.TabLayout;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.ViewPager;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.Toast;

import xyz.sleekstats.softball.MyApp;
import xyz.sleekstats.softball.R;
import xyz.sleekstats.softball.data.FirestoreUpdateService;
import xyz.sleekstats.softball.data.GameUpdateIntentMaker;
import xyz.sleekstats.softball.data.MySyncResultReceiver;
import xyz.sleekstats.softball.data.StatsContract;
import xyz.sleekstats.softball.data.StatsContract.StatsEntry;
import xyz.sleekstats.softball.data.TimeStampUpdater;
import xyz.sleekstats.softball.dialogs.AddNewPlayersDialog;
import xyz.sleekstats.softball.dialogs.ChangeTeamDialog;
import xyz.sleekstats.softball.dialogs.ChooseOrCreateTeamDialog;
import xyz.sleekstats.softball.dialogs.GameSettingsDialog;
import xyz.sleekstats.softball.dialogs.LineupSortDialog;
import xyz.sleekstats.softball.dialogs.PreviewSortDialog;
import xyz.sleekstats.softball.fragments.MatchupFragment;
import xyz.sleekstats.softball.fragments.StandingsFragment;
import xyz.sleekstats.softball.fragments.StatsFragment;
import xyz.sleekstats.softball.models.MainPageSelection;
import xyz.sleekstats.softball.models.Player;
import xyz.sleekstats.softball.models.Team;

import java.util.ArrayList;
import java.util.List;

public class LeagueManagerActivity extends ExportActivity
        implements AddNewPlayersDialog.OnListFragmentInteractionListener,
        GameSettingsDialog.OnFragmentInteractionListener,
        ChooseOrCreateTeamDialog.OnFragmentInteractionListener,
        ChangeTeamDialog.OnFragmentInteractionListener,
        MatchupFragment.OnFragmentInteractionListener,
        StandingsFragment.OnFragmentInteractionListener,
        StatsFragment.OnFragmentInteractionListener,
        MySyncResultReceiver.Receiver,
        LineupSortDialog.OnLineupSortListener,
        PreviewSortDialog.OnFragmentInteractionListener {

    private StandingsFragment standingsFragment;
    private StatsFragment statsFragment;
    private MatchupFragment matchupFragment;

    private String mLeagueID;
    private int mLevel;
    private String leagueName;
    private boolean gameUpdating;
    private MySyncResultReceiver mReceiver;

    private int localUpdate = 5;
    private int firestoreUpdate = 4;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pager);

        getStatKeeperData();
        if (mLeagueID == null) {
            goToMain();
            return;
        }

        if (savedInstanceState != null) {
            gameUpdating = savedInstanceState.getBoolean(StatsEntry.UPDATE, false);
        }
        startPager();

        if (!gameUpdating) {
            String selection = StatsEntry.COLUMN_LEAGUE_ID + "=?";
            String[] selectionArgs = new String[]{mLeagueID};

            Cursor cursor = getContentResolver().query(StatsEntry.CONTENT_URI_BACKUP_PLAYERS, null, selection, selectionArgs, null);
            if (cursor != null && cursor.moveToFirst()) {
                sendRetryGameLoadIntent();
                cursor.close();
                return;
            }
            cursor = getContentResolver().query(StatsEntry.CONTENT_URI_BACKUP_TEAMS, null, selection, selectionArgs, null);
            if (cursor != null && cursor.moveToFirst()) {
                sendRetryGameLoadIntent();
            }
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private void getStatKeeperData() {
        try {
            MyApp myApp = (MyApp) getApplicationContext();
            MainPageSelection mainPageSelection = myApp.getCurrentSelection();
            leagueName = mainPageSelection.getName();
            mLeagueID = mainPageSelection.getId();
            mLevel = mainPageSelection.getLevel();
            setTitle(leagueName + " (League)");
        } catch (Exception e) {
            goToMain();
        }
    }

    private void goToMain() {
        Intent intent = new Intent(LeagueManagerActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    private void startPager() {
        ViewPager viewPager = findViewById(R.id.my_view_pager);
        FragmentManager fragmentManager = getSupportFragmentManager();
        viewPager.setOffscreenPageLimit(2);
        viewPager.setAdapter(new LeagueManagerPagerAdapter(fragmentManager));

        TabLayout tabLayout = findViewById(R.id.my_tab_layout);
        tabLayout.setupWithViewPager(viewPager);
    }

    private void sendRetryGameLoadIntent() {
//        LocalBroadcastManager.getInstance(this).registerReceiver(mReceiver, new IntentFilter(StatsEntry.UPDATE));
        mReceiver = new MySyncResultReceiver(new Handler());
        mReceiver.setReceiver(this);
        Intent intent = new Intent(LeagueManagerActivity.this, FirestoreUpdateService.class);
        intent.putExtra(FirestoreUpdateService.STATKEEPER_ID, mLeagueID);
        intent.putExtra(StatsEntry.UPDATE, mReceiver);
        intent.setAction(FirestoreUpdateService.INTENT_RETRY_GAME_LOAD);
        startService(intent);
    }

    @Override
    public void chooseTeamDialog(ArrayList<Team> teams) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        DialogFragment newFragment = ChangeTeamDialog.newInstance(teams, null, null);
        newFragment.show(fragmentTransaction, "");
    }

    @Override
    public void onNewTeam(String teamName) {
        if (teamName.isEmpty()) {
            Toast.makeText(LeagueManagerActivity.this, "Please type a team name", Toast.LENGTH_SHORT).show();
        } else {
            ContentValues values = new ContentValues();
            values.put(StatsEntry.COLUMN_NAME, teamName);
            values.put(StatsEntry.COLUMN_LEAGUE_ID, mLeagueID);
            values.put(StatsEntry.ADD, true);
            values.put(StatsEntry.TYPE, MainPageSelection.TYPE_LEAGUE);
            values.put(StatsEntry.COLUMN_LEAGUE, leagueName);
            Uri teamUri = getContentResolver().insert(StatsEntry.CONTENT_URI_TEAMS, values);

            if (teamUri == null) {
                return;
            }
            TimeStampUpdater.updateTimeStamps(LeagueManagerActivity.this, mLeagueID, System.currentTimeMillis());

            String selection = StatsEntry.COLUMN_LEAGUE_ID + "=?";
            String[] selectionArgs = new String[]{mLeagueID};
            Cursor cursor = getContentResolver().query(teamUri, new String[]{StatsEntry._ID, StatsEntry.COLUMN_FIRESTORE_ID}, selection, selectionArgs, null);
            if (cursor.moveToFirst()) {
                int id = StatsContract.getColumnInt(cursor, StatsEntry._ID);
                String firestoreID = StatsContract.getColumnString(cursor, StatsEntry.COLUMN_FIRESTORE_ID);
                addNewPlayersDialog(teamName, firestoreID);

                if (standingsFragment != null) {
                    standingsFragment.setAdderButtonVisible();
                }
                if (statsFragment != null) {
                    statsFragment.setAdderButtonVisible();
                    statsFragment.updateTeams(teamName, id, firestoreID);
                }
            }
        }
    }

    private void addNewPlayersDialog(String teamName, String teamID) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        DialogFragment newFragment = AddNewPlayersDialog.newInstance(teamName, teamID);
        newFragment.show(fragmentTransaction, "");
    }

    @Override
    public void onCancel() {
        if (standingsFragment != null) {
            standingsFragment.setAdderButtonVisible();
        }
        if (statsFragment != null) {
            statsFragment.setAdderButtonVisible();
        }
    }

    @Override
    public void onTeamChosen(String playerFirestoreID, String teamName, String teamFirestoreID) {
        addNewPlayersDialog(teamName, teamFirestoreID);

        if (standingsFragment != null) {
            standingsFragment.setAdderButtonVisible();
        }
        if (statsFragment != null) {
            statsFragment.setAdderButtonVisible();
        }
    }

    @Override
    public void onTeamChoiceCancel() {
        if (standingsFragment != null) {
            standingsFragment.setAdderButtonVisible();
        }
        if (statsFragment != null) {
            statsFragment.setAdderButtonVisible();
        }
    }

    private void goToUserSettings() {
        Intent settingsIntent = new Intent(LeagueManagerActivity.this, UsersActivity.class);
        startActivity(settingsIntent);
    }

    private void onExport() {
        startLeagueExport(leagueName);
    }

    @Override
    public void startAdder(ArrayList<Team> teams) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        DialogFragment newFragment = ChooseOrCreateTeamDialog.newInstance(teams);
        newFragment.show(fragmentTransaction, "");
    }

    @Override
    public void clearGameDB() {
        String selection = StatsEntry.COLUMN_LEAGUE_ID + "=?";
        String[] selectionArgs = new String[]{mLeagueID};
        getContentResolver().delete(StatsEntry.CONTENT_URI_GAMELOG, selection, selectionArgs);
        getContentResolver().delete(StatsEntry.CONTENT_URI_TEMP, selection, selectionArgs);
        SharedPreferences savedGamePreferences = getSharedPreferences(mLeagueID + StatsEntry.GAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = savedGamePreferences.edit();
        editor.clear();
        editor.apply();
    }

    @Override
    public void goToGameSettings() {
        SharedPreferences settingsPreferences = getSharedPreferences(mLeagueID + StatsEntry.SETTINGS, Context.MODE_PRIVATE);
        int innings = settingsPreferences.getInt(StatsEntry.INNINGS, 7);
        int genderSorter = settingsPreferences.getInt(StatsEntry.COLUMN_GENDER, 0);
        int mercyRuns = settingsPreferences.getInt(StatsEntry.MERCY, 99);
        boolean gameHelp = settingsPreferences.getBoolean(StatsEntry.HELP, true);
        boolean extraGirlsSorted = settingsPreferences.getBoolean(StatsEntry.SORT_GIRLS, false);
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        DialogFragment newFragment = GameSettingsDialog.newInstance(innings, genderSorter, mercyRuns, mLeagueID, 0, gameHelp, extraGirlsSorted);
        newFragment.show(fragmentTransaction, "");
    }

    @Override
    public void startGameActivity(String awayID, String homeID, int inningAmt, int sortArg, int femaleOrder, int mercyRuns) {

        SharedPreferences gamePreferences = getSharedPreferences(mLeagueID + StatsEntry.GAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = gamePreferences.edit();
        editor.putString(StatsEntry.COLUMN_AWAY_TEAM, awayID);
        editor.putString(StatsEntry.COLUMN_HOME_TEAM, homeID);
        editor.putInt(GameActivity.KEY_TOTALINNINGS, inningAmt);
        editor.putInt(GameActivity.KEY_GENDERSORT, sortArg);
        editor.putInt(GameActivity.KEY_FEMALEORDER, femaleOrder);
        editor.putInt(StatsEntry.MERCY, mercyRuns);
        editor.apply();

        goToGameActivity();
    }


    @Override
    public void goToGameActivity() {
        Intent intent = new Intent(LeagueManagerActivity.this, LeagueGameActivity.class);
        startActivityForResult(intent, GameActivity.REQUEST_CODE_GAME);
    }

    @Override
    public void onReceiveResult(int resultCode, Bundle resultData) {

        switch (resultCode) {
            case FirestoreUpdateService.MSG_UPDATE_SUCCESS:
                localUpdate--;
                break;

            case FirestoreUpdateService.MSG_FIRESTORE_SUCCESS:
                firestoreUpdate--;
                break;

            case FirestoreUpdateService.MSG_TRANSFER_SUCCESS:
                localUpdate--;
                if (matchupFragment != null) {
                    matchupFragment.setPostGameLayout(true);
                }
                break;

            case FirestoreUpdateService.MSG_FIRESTORE_FAILURE:
                Toast.makeText(LeagueManagerActivity.this, R.string.cloud_fail, Toast.LENGTH_LONG).show();
                break;

            case FirestoreUpdateService.MSG_RETRY_SUCCESS:
                Toast.makeText(LeagueManagerActivity.this, R.string.stat_update_success, Toast.LENGTH_LONG).show();
                break;

            case FirestoreUpdateService.MSG_RETRY_FAILURE:
                Toast.makeText(LeagueManagerActivity.this, getString(R.string.cloud_fail) +
                        "\nIf the problem persists, please contact me at sleekstats@gmail.com", Toast.LENGTH_LONG).show();
                break;

            case FirestoreUpdateService.MSG_TRANSFER_FAILURE:
                Toast.makeText(LeagueManagerActivity.this, "Error transferring stats.\nPlease try again.", Toast.LENGTH_LONG).show();
                localUpdate = 999;
                if (matchupFragment != null) {
                    matchupFragment.onTransferError();
                }
                break;
        }
        boolean localUpdateFinish = localUpdate < 1;
        boolean firestoreUpdateFinish = firestoreUpdate < 1;

        if (localUpdateFinish) {
            localUpdate = 5;
            if (standingsFragment != null) {
                standingsFragment.reloadStandings();
            }
            if (statsFragment != null) {
                statsFragment.reloadStats();
            }
            Toast.makeText(LeagueManagerActivity.this, R.string.stat_update_success, Toast.LENGTH_SHORT).show();
            gameUpdating = false;
        }
        if (firestoreUpdateFinish) {
            firestoreUpdate = 4;
            Toast.makeText(LeagueManagerActivity.this, R.string.changes_to_cloud, Toast.LENGTH_SHORT).show();
        }
        if (localUpdateFinish && firestoreUpdateFinish) {
//            LocalBroadcastManager.getInstance(LeagueManagerActivity.this).unregisterReceiver(mReceiver);
            if (mReceiver != null) {
                mReceiver.setReceiver(null);
            }
        }
    }

    @Override
    public void onLineupSortChoice(String awayID, String homeID, int innings, int sortArg, int femaleOrder) {

        if (sortArg < 0) {
            ContentValues values = new ContentValues();
            values.put(StatsEntry.COLUMN_LEAGUE_ID, mLeagueID);
            values.put(StatsEntry.COLUMN_FIRESTORE_ID, GameActivity.AUTO_OUT);
            values.put(StatsEntry.COLUMN_NAME, GameActivity.AUTO_OUT);
            values.put(StatsEntry.COLUMN_GENDER, 1);
            values.put(StatsEntry.COLUMN_PLAYERID, -1);
            values.put(StatsEntry.COLUMN_ORDER, 101);

            switch (sortArg) {
                case -3:
                    values.put(StatsEntry.COLUMN_TEAM_FIRESTORE_ID, awayID);
                    getContentResolver().insert(StatsEntry.CONTENT_URI_TEMP, values);

                    values.put(StatsEntry.COLUMN_TEAM_FIRESTORE_ID, homeID);
                    getContentResolver().insert(StatsEntry.CONTENT_URI_TEMP, values);
                    break;

                case -2:
                    values.put(StatsEntry.COLUMN_TEAM_FIRESTORE_ID, homeID);
                    getContentResolver().insert(StatsEntry.CONTENT_URI_TEMP, values);
                    break;

                case -1:
                    values.put(StatsEntry.COLUMN_TEAM_FIRESTORE_ID, awayID);
                    getContentResolver().insert(StatsEntry.CONTENT_URI_TEMP, values);
                    break;
            }
        }

        SharedPreferences settingsPreferences = getSharedPreferences(mLeagueID + StatsEntry.SETTINGS, Context.MODE_PRIVATE);
        int mercyRuns = settingsPreferences.getInt(StatsEntry.MERCY, 99);
        startGameActivity(awayID, homeID, innings, sortArg, femaleOrder, mercyRuns);
    }


    @Override
    public void onCancelStart() {
        if (matchupFragment != null) {
            matchupFragment.setStartButtonClickable();
        }
    }

    @Override
    public void onShowPreview(int sortArg, int femaleRequired) {
        if (matchupFragment != null) {
            ArrayList<Player> awayList;
            ArrayList<Player> homeList;
            if (sortArg < 0) {
                awayList = addAutoOuts(matchupFragment.getAwayLineupCopy(), femaleRequired);
                homeList = addAutoOuts(matchupFragment.getHomeLineupCopy(), femaleRequired);
            } else if (sortArg > 0) {
                awayList = genderSort(matchupFragment.getAwayLineupCopy(), femaleRequired + 1);
                homeList = genderSort(matchupFragment.getHomeLineupCopy(), femaleRequired + 1);
            } else {
                awayList = matchupFragment.getAwayLineupCopy();
                homeList = matchupFragment.getHomeLineupCopy();
            }
            FragmentManager fragmentManager = getSupportFragmentManager();
            FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
            DialogFragment newFragment = PreviewSortDialog.newInstance(awayList, homeList);
            newFragment.setCancelable(false);
            newFragment.show(fragmentTransaction, "");
        }
    }

    private ArrayList<Player> genderSort(ArrayList<Player> team, int femaleRequired) {

        List<Player> females = new ArrayList<>();
        List<Player> males = new ArrayList<>();
        int femaleIndex = 0;
        int maleIndex = 0;
        int firstFemale = 0;
        boolean firstFemaleSet = false;
        for (Player player : team) {
            if (player.getGender() == 1) {
                females.add(player);
                firstFemaleSet = true;
            } else {
                males.add(player);
            }
            if (!firstFemaleSet) {
                firstFemale++;
            }
        }
        if (females.isEmpty() || males.isEmpty()) {
            return team;
        }
        team.clear();
        if (firstFemale >= femaleRequired) {
            firstFemale = femaleRequired - 1;
        }
        for (int i = 0; i < firstFemale; i++) {
            team.add(males.get(maleIndex));
            maleIndex++;
            if (maleIndex >= males.size()) {
                maleIndex = 0;
            }
        }
        for (int i = 0; i < 100; i++) {
            if (i % femaleRequired == 0) {
                team.add(females.get(femaleIndex));
                femaleIndex++;
                if (femaleIndex >= females.size()) {
                    femaleIndex = 0;
                }
            } else {
                team.add(males.get(maleIndex));
                maleIndex++;
                if (maleIndex >= males.size()) {
                    maleIndex = 0;
                }
            }
        }
        return team;
    }


    private ArrayList<Player> addAutoOuts(ArrayList<Player> team, int femaleRequired) {
        boolean firstPlayerMale = team.get(0).getGender() == 0;

        String teamid = team.get(0).getTeamfirestoreid();
        int menInARow = 0;
        int womenInARow = 0;
        int menInARowToStart = 0;
        int womenInARowToStart = 0;
        boolean toStart = true;

        for (int i = 0; i < team.size(); i++) {

            Player player = team.get(i);
            if (player.getGender() == 1) {
                womenInARow++;
                menInARow = 0;
                if (firstPlayerMale) {
                    toStart = false;
                }
            } else {
                menInARow++;
                womenInARow = 0;
                if (!firstPlayerMale) {
                    toStart = false;
                }
            }

            if (womenInARow > 1) {
                team.add(i, new Player(GameActivity.AUTO_OUT, "(AUTO-OUT)", teamid, 0));
                womenInARow = 1;
                i++;
                toStart = false;
            }
            if (menInARow > femaleRequired) {
                team.add(i, new Player(GameActivity.AUTO_OUT, "(AUTO-OUT)", teamid, 1));
                menInARow = 1;
                i++;
                toStart = false;
            }

            if (toStart) {
                if (womenInARow > 0) {
                    womenInARowToStart++;
                }
                if (menInARow > 0) {
                    menInARowToStart++;
                }
            }
        }
        if (menInARow + menInARowToStart > femaleRequired) {
            team.add(new Player(GameActivity.AUTO_OUT, "(AUTO-OUT)", teamid, 1));
        }
        if (womenInARow + womenInARowToStart > 1) {
            team.add(new Player(GameActivity.AUTO_OUT, "(AUTO-OUT)", teamid, 0));
        }

        return team;
    }

    @Override
    public void onReturnToSort() {
        if (matchupFragment != null) {
            matchupFragment.openLineupSortDialog(1);
        }
    }

    private class LeagueManagerPagerAdapter extends FragmentStatePagerAdapter {

        LeagueManagerPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case 0:
                    return StandingsFragment.newInstance(mLeagueID, mLevel, leagueName);
                case 1:
                    return StatsFragment.newInstance(mLeagueID, mLevel, leagueName);
                case 2:
                    if (levelUnauthorized()) {
                        return null;
                    }
                    return MatchupFragment.newInstance(mLeagueID, leagueName);
                default:
                    return null;
            }
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 0:
                    return getString(R.string.standings);
                case 1:
                    return getString(R.string.stats);
                case 2:
                    return getString(R.string.game);
                default:
                    return null;
            }
        }

        @Override
        public int getCount() {
            if (levelUnauthorized()) {
                return 2;
            }
            return 3;
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            Fragment createdFragment = (Fragment) super.instantiateItem(container, position);

            switch (position) {
                case 0:
                    standingsFragment = (StandingsFragment) createdFragment;
                    break;
                case 1:
                    statsFragment = (StatsFragment) createdFragment;
                    break;
                case 2:
                    matchupFragment = (MatchupFragment) createdFragment;
                    break;
            }
            return createdFragment;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences settingsPreferences = getSharedPreferences(mLeagueID + StatsEntry.SETTINGS, Context.MODE_PRIVATE);
        int innings = settingsPreferences.getInt(StatsEntry.INNINGS, 7);
        int genderSorter = settingsPreferences.getInt(StatsEntry.COLUMN_GENDER, 0);
        onGameSettingsChanged(innings, genderSorter, 0);
        boolean genderSettingsOn = genderSorter != 0;

        if (statsFragment != null) {
            statsFragment.changeColorsRV(genderSettingsOn);
        }

        if (matchupFragment != null) {
            matchupFragment.setGameSettings();
            matchupFragment.updateBenchColors();
            matchupFragment.changeColorsRV(genderSettingsOn);
//            matchupFragment.updateMatchup();
        }
    }

    @Override
    public void onSubmitPlayersListener(List<String> names, List<Integer> genders, String team, String teamID) {
        boolean update = false;

        for (int i = 0; i < names.size() - 1; i++) {
            ContentValues values = new ContentValues();
            String player = names.get(i);
            if (player.isEmpty()) {
                continue;
            }
            int gender = genders.get(i);
            values.put(StatsEntry.COLUMN_NAME, player);
            values.put(StatsEntry.COLUMN_GENDER, gender);
            values.put(StatsEntry.COLUMN_ORDER, i + 1);
            values.put(StatsEntry.COLUMN_TEAM, team);
            values.put(StatsEntry.COLUMN_TEAM_FIRESTORE_ID, teamID);
            values.put(StatsEntry.ADD, true);
            values.put(StatsEntry.COLUMN_LEAGUE_ID, mLeagueID);
            Uri uri = getContentResolver().insert(StatsContract.StatsEntry.CONTENT_URI_PLAYERS, values);
            if (uri != null) {
                update = true;
            }
        }
        if (update) {
            TimeStampUpdater.updateTimeStamps(LeagueManagerActivity.this, mLeagueID, System.currentTimeMillis());
        }

        if (matchupFragment != null) {
            matchupFragment.updateMatchup();
        }
    }

    @Override
    public void onGameSettingsChanged(int innings, int genderSorter, int mercyRuns) {
        boolean genderSettingsOn = genderSorter != 0;

        if (statsFragment != null) {
            statsFragment.changeColorsRV(genderSettingsOn);
        }

        if (matchupFragment != null) {
            matchupFragment.setGameSettings();
            matchupFragment.updateBenchColors();
            matchupFragment.changeColorsRV(genderSettingsOn);
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        if (mReceiver != null) {
            mReceiver.setReceiver(null);
        }
        goToMain();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_league, menu);
        return true;
    }

    private boolean levelUnauthorized() {
        return mLevel < UsersActivity.LEVEL_VIEW_WRITE;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (levelUnauthorized()) {
            menu.findItem(R.id.change_game_settings).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                goToMain();
                return true;
            case R.id.change_user_settings:
                goToUserSettings();
                return true;
            case R.id.change_game_settings:
                goToGameSettings();
                return true;
            case R.id.action_export_stats:
                onExport();
                return true;
        }
        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == GameActivity.REQUEST_CODE_GAME && resultCode == GameActivity.RESULT_CODE_GAME_FINISHED) {
            gameUpdating = true;
            updateStats(data);
        }
    }

    private void updateStats(Intent data) {

        if (matchupFragment != null) {
            matchupFragment.setPostGameLayout(false);
        }

        if (mLeagueID == null) {
            getStatKeeperData();
        }
        long updateTime = System.currentTimeMillis();

        String awayID = data.getStringExtra(StatsEntry.COLUMN_AWAY_TEAM);
        String homeID = data.getStringExtra(StatsEntry.COLUMN_HOME_TEAM);
        int awayTeamRuns = data.getIntExtra(StatsEntry.COLUMN_AWAY_RUNS, -1);
        int homeTeamRuns = data.getIntExtra(StatsEntry.COLUMN_HOME_RUNS, -1);

//        LocalBroadcastManager.getInstance(this).registerReceiver(mReceiver, new IntentFilter(StatsEntry.UPDATE));
        mReceiver = new MySyncResultReceiver(new Handler());
        mReceiver.setReceiver(this);

        Context context = LeagueManagerActivity.this;
        startService(GameUpdateIntentMaker.getTransferIntent(context, updateTime, awayID, homeID, awayTeamRuns, homeTeamRuns, mLeagueID, mReceiver));
        startService(GameUpdateIntentMaker.getTeamIntent(context, updateTime, awayID, awayTeamRuns, homeTeamRuns, mLeagueID, mReceiver));
        startService(GameUpdateIntentMaker.getTeamIntent(context, updateTime, homeID, homeTeamRuns, awayTeamRuns, mLeagueID, mReceiver));
        startService(GameUpdateIntentMaker.getPlayersIntent(context, updateTime, mLeagueID, mReceiver));
        startService(GameUpdateIntentMaker.getBoxscoreIntent(context, updateTime, awayID, homeID, awayTeamRuns, homeTeamRuns, mLeagueID, mReceiver));
//        TimeStampUpdater.updateTimeStamps(this, mLeagueID, updateTime);
    }

    @Override
    protected void onStop() {
        super.onStop();
//        LocalBroadcastManager.getInstance(this).unregisterReceiver(mReceiver);
        if (mReceiver != null) {
            mReceiver.setReceiver(null);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (gameUpdating) {
//            LocalBroadcastManager.getInstance(this).registerReceiver(mReceiver, new IntentFilter(StatsEntry.UPDATE));
            if (mReceiver == null) {
                mReceiver = new MySyncResultReceiver(new Handler());
            }
            mReceiver.setReceiver(this);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(StatsEntry.UPDATE, gameUpdating);
        super.onSaveInstanceState(outState);
    }
}