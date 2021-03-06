package xyz.sleekstats.softball.activities;

import android.content.ClipData;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.ads.consent.ConsentInfoUpdateListener;
import com.google.ads.consent.ConsentInformation;
import com.google.ads.consent.ConsentStatus;
import com.google.ads.mediation.admob.AdMobAdapter;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

import xyz.sleekstats.softball.R;
import xyz.sleekstats.softball.data.StatsContract;
import xyz.sleekstats.softball.data.StatsContract.StatsEntry;
import xyz.sleekstats.softball.dialogs.AddRunsDialog;
import xyz.sleekstats.softball.dialogs.EditWarningDialog;
import xyz.sleekstats.softball.dialogs.EndOfGameDialog;
import xyz.sleekstats.softball.dialogs.FinishGameConfirmationDialog;
import xyz.sleekstats.softball.dialogs.GameSettingsDialog;
import xyz.sleekstats.softball.dialogs.SaveDeleteGameDialog;
import xyz.sleekstats.softball.models.BaseLog;
import xyz.sleekstats.softball.models.Player;

public abstract class GameActivity extends AppCompatActivity
        implements EndOfGameDialog.OnFragmentInteractionListener,
        SaveDeleteGameDialog.OnFragmentInteractionListener,
        FinishGameConfirmationDialog.OnFragmentInteractionListener,
        GameSettingsDialog.OnFragmentInteractionListener,
        EditWarningDialog.OnFragmentInteractionListener,
        AddRunsDialog.OnFragmentInteractionListener {

    Cursor gameCursor;

    TextView scoreboardAwayName;
    TextView scoreboardHomeName;
    TextView scoreboardAwayScore;
    TextView scoreboardHomeScore;
    TextView nowBatting;
    TextView outsDisplay;
    TextView avgDisplay;
    TextView rbiDisplay;
    TextView runDisplay;
    TextView hrDisplay;
    private TextView mercyDisplay;
    private TextView counterDisplay;
    private TextView inningDisplay;
    private ImageView inningTopArrow;
    private ImageView inningBottomArrow;
    private ImageView undoButton;
    private ImageView redoButton;

    private Button submitPlay;
    private Button resetBasesBtn;

    private RadioGroup group1;
    private RadioGroup group2;
    private String result;

    private RelativeLayout batterDisplay;
    private TextView batterText;
    TextView firstDisplay;
    TextView secondDisplay;
    TextView thirdDisplay;
    private TextView homeDisplay;
    private ImageView outTrash;

    TextView step1View;
    TextView step2View;
    TextView step3View;
    TextView step4View;


    String awayTeamName;
    String homeTeamName;
    int awayTeamRuns;
    int homeTeamRuns;
    int inningRuns;
    int mercyRuns;

    String tempBatter;
    int inningChanged = 0;
    int inningNumber = 2;
    int gameOuts = 0;
    private int tempOuts;
    private int tempRuns;
    private int countAway;
    private int countHome;

    Player currentBatter;
    private Drawable mRunner;

    private static final NumberFormat formatter = new DecimalFormat("#.000");
    BaseLog currentBaseLogStart;
    ArrayList<String> currentRunsLog;
    ArrayList<String> tempRunsLog;

    int gameLogIndex;
    int lowestIndex;
    int highestIndex;
    boolean undoRedo = false;

    boolean finalInning;
    boolean redoEndsGame = false;
    boolean gameHelp;

    private boolean playEntered = false;
    private boolean batterMoved = false;
    private boolean firstOccupied = false;
    private boolean secondOccupied = false;
    private boolean thirdOccupied = false;
    private boolean mResetListeners = false;
    private boolean mResetDrag;
    int totalInnings;

    static final String KEY_GAMELOGINDEX = "keyGameLogIndex";
    static final String KEY_LOWESTINDEX = "keyLowestIndex";
    static final String KEY_HIGHESTINDEX = "keyHighestIndex";
    public static final String KEY_GENDERSORT = "keyGenderSort";
    public static final String KEY_FEMALEORDER = "keyFemaleOrder";
    static final String KEY_INNINGNUMBER = "keyInningNumber";
    public static final String KEY_TOTALINNINGS = "keyTotalInnings";
    static final String KEY_UNDOREDO = "keyUndoRedo";
    static final String KEY_REDOENDSGAME = "redoEndsGame";
    private static final String DIALOG_FINISH = "DialogFinish";
    public static final int RESULT_CODE_GAME_FINISHED = 222;
    public static final int REQUEST_CODE_GAME = 111;
    public static final int RESULT_CODE_EDITED = 444;
    static final int REQUEST_CODE_EDIT = 333;
    public static final String AUTO_OUT = "AUTO-OUT";

    String mSelectionID;
    private Toast mCurrentToast;
    private String mToastText = "";

    private final MyTouchListener myTouchListener = new MyTouchListener();
    private final MyDragListener myDragListener = new MyDragListener();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!getSelectionData()) {
            goToMain();
            return;
        }
        setCustomViews();
        setViews();

        String selection = StatsEntry.COLUMN_LEAGUE_ID + "=?";
        String[] selectionArgs = new String[]{mSelectionID};
        gameCursor = getContentResolver().query(StatsEntry.CONTENT_URI_GAMELOG, null,
                selection, selectionArgs, null);
        if (gameCursor.moveToFirst()) {
            loadGamePreferences();
            Bundle args = getIntent().getExtras();
            if (args != null) {
                if (args.getBoolean("edited") && undoRedo) {
                    deleteGameLogs();
                    highestIndex = gameLogIndex;
                    invalidateOptionsMenu();
                    setUndoRedo();
                }
            }
            resumeGame();
            return;
        }
        setInningDisplay();
        finalInning = false;
        startGame();

        if(savedInstanceState != null) {
            countAway = savedInstanceState.getInt("countAway");
            countHome = savedInstanceState.getInt("countHome");
        }
    }

    private void checkForConsent() {
        ConsentInformation consentInformation = ConsentInformation.getInstance(GameActivity.this);
        String[] publisherIds = {"pub-5443559095909539"};

        if(!consentInformation.isRequestLocationInEeaOrUnknown()) {
            AdRequest adRequest = new AdRequest.Builder()
                    .build();
            AdView adView = findViewById(R.id.game_ad);
            adView.loadAd(adRequest);
            return;
        }
        consentInformation.requestConsentInfoUpdate(publisherIds, new ConsentInfoUpdateListener() {
            @Override
            public void onConsentInfoUpdated(ConsentStatus consentStatus) {
                AdRequest adRequest;
                switch (consentStatus) {
                    case PERSONALIZED:
                        adRequest = new AdRequest.Builder()
                                .addTestDevice(AdRequest.DEVICE_ID_EMULATOR)
                                .addTestDevice("E86B9D16D56D2E888CC1EF6694718979")
                                .build();
                        break;
                    case NON_PERSONALIZED:
                        Bundle extras = new Bundle();
                        extras.putString("npa", "1");

                        adRequest = new AdRequest.Builder()
                                .addNetworkExtrasBundle(AdMobAdapter.class, extras)
                                .build();
                        break;
                    default:
                        return;
                }
                AdView adView = findViewById(R.id.game_ad);
                adView.loadAd(adRequest);
            }

            @Override
            public void onFailedToUpdateConsentInfo(String errorDescription) {
            }
        });
    }

    protected abstract boolean getSelectionData();

    private void goToMain() {
        Intent intent = new Intent(GameActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    private void setViews() {

        checkForConsent();

        nowBatting = findViewById(R.id.nowbatting);
        outsDisplay = findViewById(R.id.num_of_outs);
        avgDisplay = findViewById(R.id.avgdisplay);
        rbiDisplay = findViewById(R.id.rbidisplay);
        runDisplay = findViewById(R.id.rundisplay);
        hrDisplay = findViewById(R.id.hrdisplay);
        inningDisplay = findViewById(R.id.inning);
        inningTopArrow = findViewById(R.id.inning_top_arrow);
        inningBottomArrow = findViewById(R.id.inning_bottom_arrow);
        RadioButton sbBtn = findViewById(R.id.sb_rb);
        RadioButton kBtn = findViewById(R.id.k_rb);
        RadioButton hbpBtn = findViewById(R.id.hbp_rb);
        counterDisplay = findViewById(R.id.counter_display);
        SharedPreferences sharedPreferences = getSharedPreferences(mSelectionID + StatsEntry.SETTINGS, Context.MODE_PRIVATE);
        boolean sbOn = sharedPreferences.getBoolean(StatsEntry.COLUMN_SB, false);
        if (sbOn) {
            sbBtn.setVisibility(View.VISIBLE);
            kBtn.setVisibility(View.VISIBLE);
            hbpBtn.setVisibility(View.VISIBLE);
        } else {
            sbBtn.setVisibility(View.GONE);
            kBtn.setVisibility(View.GONE);
            hbpBtn.setVisibility(View.GONE);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mRunner = getDrawable(R.drawable.ic_directions_run_black_18dp);
            mRunner.setAlpha(25);
        }

        group1 = findViewById(R.id.group1);
        group2 = findViewById(R.id.group2);

        mercyDisplay = findViewById(R.id.mercy_display);
        mercyDisplay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openGameSettingsDialog();
            }
        });

        submitPlay = findViewById(R.id.submit);
        submitPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onSubmit();
            }
        });
        disableSubmitButton();

        resetBasesBtn = findViewById(R.id.reset);
        resetBasesBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resetBases(currentBaseLogStart);
            }
        });
        disableResetButton();

        undoButton = findViewById(R.id.btn_undo);
        undoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                undoButton.setEnabled(false);
                undoPlay();
            }
        });

        redoButton = findViewById(R.id.btn_redo);
        redoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                redoButton.setEnabled(false);
                redoPlay();
            }
        });

        batterDisplay = findViewById(R.id.batter);
        batterText = findViewById(R.id.batter_name_view);
        firstDisplay = findViewById(R.id.first_display);
        secondDisplay = findViewById(R.id.second_display);
        thirdDisplay = findViewById(R.id.third_display);
        homeDisplay = findViewById(R.id.home_display);
        homeDisplay.bringToFront();
        outTrash = findViewById(R.id.trash);
        batterDisplay.setOnTouchListener(myTouchListener);
        firstDisplay.setOnDragListener(myDragListener);
        secondDisplay.setOnDragListener(myDragListener);
        thirdDisplay.setOnDragListener(myDragListener);
        homeDisplay.setOnDragListener(myDragListener);
        outTrash.setOnDragListener(myDragListener);

        findViewById(R.id.counter_plus).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(inningNumber % 2 == 0) {
                    countAway++;
                    counterDisplay.setText(String.valueOf(countAway));
                } else {
                    countHome++;
                    counterDisplay.setText(String.valueOf(countHome));
                }
            }
        });
        findViewById(R.id.counter_minus).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(inningNumber % 2 == 0) {
                    countAway--;
                    counterDisplay.setText(String.valueOf(countAway));
                } else {
                    countHome--;
                    counterDisplay.setText(String.valueOf(countHome));
                }
            }
        });
    }

    protected abstract void loadGamePreferences();

    protected abstract void setCustomViews();

    ArrayList<Player> setTeam(String teamID) {
        String selection = StatsEntry.COLUMN_TEAM_FIRESTORE_ID + "=? AND " + StatsEntry.COLUMN_LEAGUE_ID + "=?";
        String[] selectionArgs = new String[]{teamID, mSelectionID};
        String sortOrder = StatsEntry.COLUMN_ORDER + " ASC";
        Cursor cursor = getContentResolver().query(StatsEntry.CONTENT_URI_TEMP, null,
                selection, selectionArgs, sortOrder);

        ArrayList<Player> team = new ArrayList<>();
        while (cursor.moveToNext()) {
            int order = StatsContract.getColumnInt(cursor, StatsEntry.COLUMN_ORDER);
            if (order > 100) {
                continue;
            }
            Player player = new Player(cursor, true);
            team.add(player);
        }
        return team;
    }

    List<Player> genderSort(List<Player> team, int femaleRequired) {
        if (femaleRequired == 0) {
            return team;
        }

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

    List<Player> addAutoOuts(List<Player> team, int femaleRequired) {
        if (femaleRequired < 1) {
            return team;
        }
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
                team.add(i, new Player(AUTO_OUT, "(AUTO-OUT)", teamid, 0));
                womenInARow = 1;
                i++;
                toStart = false;
            }
            if (menInARow > femaleRequired - 1) {
                team.add(i, new Player(AUTO_OUT, "(AUTO-OUT)", teamid, 1));
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

        if (menInARow + menInARowToStart > femaleRequired - 1) {
            team.add(new Player(AUTO_OUT, "(AUTO-OUT)", teamid, 1));
        }
        if (womenInARow + womenInARowToStart > 1) {
            team.add(new Player(AUTO_OUT, "(AUTO-OUT)", teamid, 0));
        }

        return team;
    }


    public void onRadioButtonClicked(View view) {
        ((RadioButton) view).setChecked(true);
        playEntered = true;
        switch (view.getId()) {
            case R.id.single_rb:
                result = StatsEntry.COLUMN_1B;
                group2.clearCheck();
                break;
            case R.id.dbl_rb:
                result = StatsEntry.COLUMN_2B;
                group2.clearCheck();
                break;
            case R.id.triple_rb:
                result = StatsEntry.COLUMN_3B;
                group2.clearCheck();
                break;
            case R.id.hr_rb:
                result = StatsEntry.COLUMN_HR;
                group2.clearCheck();
                break;
            case R.id.bb_rb:
                result = StatsEntry.COLUMN_BB;
                group2.clearCheck();
                break;
            case R.id.k_rb:
                result = StatsEntry.COLUMN_K;
                group2.clearCheck();
                break;
            case R.id.out_rb:
                result = StatsEntry.COLUMN_OUT;
                group1.clearCheck();
                break;
            case R.id.roe_rb:
                result = StatsEntry.COLUMN_ROE;
                group1.clearCheck();
                break;
            case R.id.fc_rb:
                result = StatsEntry.COLUMN_FC;
                group1.clearCheck();
                break;
            case R.id.sf_rb:
                result = StatsEntry.COLUMN_SF;
                group1.clearCheck();
                break;
            case R.id.sacbunt_rb:
                result = StatsEntry.COLUMN_SAC_BUNT;
                group1.clearCheck();
                break;
            case R.id.hbp_rb:
                result = StatsEntry.COLUMN_HBP;
                group1.clearCheck();
                break;
            case R.id.sb_rb:
                group1.clearCheck();
                if (!batterMoved) {
                    result = StatsEntry.COLUMN_SB;
                    enableSubmitButton();
                } else {
                    cancelSBClick();
                    Toast.makeText(GameActivity.this,
                            "Can't set stolen bases if batter has moved!", Toast.LENGTH_SHORT).show();
                }
                return;
        }
        if (batterMoved) {
            enableSubmitButton();
        } else {
            disableSubmitButton();
        }
    }

    private void setBaseListeners() {
        firstDisplay.setAlpha(1f);
        secondDisplay.setAlpha(1f);
        thirdDisplay.setAlpha(1f);
        batterDisplay.setAlpha(1f);
        if (firstDisplay.getText().toString().isEmpty()) {
            firstDisplay.setOnTouchListener(null);
            firstDisplay.setOnDragListener(myDragListener);
            firstOccupied = false;
        } else {
            firstDisplay.setOnTouchListener(myTouchListener);
            firstDisplay.setOnDragListener(null);
            firstOccupied = true;
        }

        if (secondDisplay.getText().toString().isEmpty()) {
            secondDisplay.setOnTouchListener(null);
            secondDisplay.setOnDragListener(myDragListener);
            secondOccupied = false;
        } else {
            secondDisplay.setOnTouchListener(myTouchListener);
            secondDisplay.setOnDragListener(null);
            secondOccupied = true;
        }

        if (thirdDisplay.getText().toString().isEmpty()) {
            thirdDisplay.setOnTouchListener(null);
            thirdDisplay.setOnDragListener(myDragListener);
            thirdOccupied = false;
        } else {
            thirdDisplay.setOnTouchListener(myTouchListener);
            thirdDisplay.setOnDragListener(null);
            thirdOccupied = true;
        }
        batterDisplay.setOnTouchListener(myTouchListener);
        homeDisplay.setOnDragListener(myDragListener);
    }

    void startGame() {
        SharedPreferences sharedPreferences = getSharedPreferences(mSelectionID + StatsEntry.SETTINGS, Context.MODE_PRIVATE);
        gameHelp = sharedPreferences.getBoolean(StatsEntry.HELP, true);
        if (gameHelp) {
            step1View = findViewById(R.id.step1text);
            step2View = findViewById(R.id.step2text);
            step3View = findViewById(R.id.step3text);
            step4View = findViewById(R.id.step4text);
            step1View.setVisibility(View.VISIBLE);
            step2View.setVisibility(View.VISIBLE);
            step3View.setVisibility(View.VISIBLE);
            step4View.setVisibility(View.VISIBLE);
            submitPlay.setOnClickListener(null);

            submitPlay.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    step1View.setVisibility(View.GONE);
                    step2View.setVisibility(View.GONE);
                    step3View.setVisibility(View.GONE);
                    step4View.setVisibility(View.GONE);
                    onSubmit();
                    submitPlay.setOnClickListener(null);
                    submitPlay.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            onSubmit();
                        }
                    });
                }
            });
        }
    }

    protected abstract void resumeGame();

    void nextBatter() {
        if (!isTopOfInning() && finalInning && homeTeamRuns > awayTeamRuns) {
            if (isLeagueGameOrHomeTeam()) {
                increaseLineupIndex();
            }
            showFinishGameDialog();
            return;
        }
        if (gameOuts >= 3) {
            if (!isTopOfInning() && finalInning && awayTeamRuns > homeTeamRuns) {
                if (isLeagueGameOrHomeTeam()) {
                    increaseLineupIndex();
                }
                showFinishGameDialog();
                return;
            } else {
                nextInning();
                if (isTeamAlternate()) {
                    increaseLineupIndex();
                }
            }
        } else {
            increaseLineupIndex();
        }
        enableSubmitButton();
        updateGameLogs();
    }

    private void nextAfterSB() {
        if (!isTopOfInning() && finalInning && homeTeamRuns > awayTeamRuns) {
            showFinishGameDialog();
            return;
        }
        if (gameOuts >= 3) {
            if (!isTopOfInning() && finalInning && awayTeamRuns > homeTeamRuns) {
                showFinishGameDialog();
                return;
            } else {
                if (isLeagueGame()) {
                    decreaseLineupIndex();
                }
                nextInning();
            }
        }
        enableSubmitButton();
        updateGameLogs();
    }

    protected abstract boolean isLeagueGame();

    protected abstract boolean isLeagueGameOrHomeTeam();

    protected abstract void updateGameLogs();

    void clearTempState() {
        group1.clearCheck();
        group2.clearCheck();
        tempRunsLog.clear();
        currentRunsLog.clear();
        disableSubmitButton();
        disableResetButton();
        tempOuts = 0;
        tempRuns = 0;
        playEntered = false;
        batterMoved = false;
        inningChanged = 0;
    }

    void enterGameValues(BaseLog currentBaseLogEnd, int team,
                         String previousBatterID, String onDeckID) {

        String first = currentBaseLogEnd.getBasepositions()[0];
        String second = currentBaseLogEnd.getBasepositions()[1];
        String third = currentBaseLogEnd.getBasepositions()[2];

        ContentValues values = new ContentValues();
        values.put(StatsEntry.COLUMN_LEAGUE_ID, mSelectionID);
        values.put(StatsEntry.COLUMN_PLAY, result);
        values.put(StatsEntry.COLUMN_TEAM, team);
        values.put(StatsEntry.COLUMN_BATTER, previousBatterID);
        values.put(StatsEntry.COLUMN_ONDECK, onDeckID);
        values.put(StatsEntry.COLUMN_INNING_CHANGED, inningChanged);
        values.put(StatsEntry.INNINGS, inningNumber);
        values.put(StatsEntry.COLUMN_OUT, gameOuts);
        values.put(StatsEntry.COLUMN_1B, first);
        values.put(StatsEntry.COLUMN_2B, second);
        values.put(StatsEntry.COLUMN_3B, third);
        values.put(StatsEntry.COLUMN_AWAY_RUNS, awayTeamRuns);
        values.put(StatsEntry.COLUMN_HOME_RUNS, homeTeamRuns);
        values.put(StatsEntry.COLUMN_INNING_RUNS, inningRuns);

        for (int i = 0; i < currentRunsLog.size(); i++) {
            String player = currentRunsLog.get(i);
            switch (i) {
                case 0:
                    values.put(StatsEntry.COLUMN_RUN1, player);
                    break;
                case 1:
                    values.put(StatsEntry.COLUMN_RUN2, player);
                    break;
                case 2:
                    values.put(StatsEntry.COLUMN_RUN3, player);
                    break;
                case 3:
                    values.put(StatsEntry.COLUMN_RUN4, player);
                    break;
                default:
                    break;
            }
        }
        getContentResolver().insert(StatsEntry.CONTENT_URI_GAMELOG, values);
        saveGameState();
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveGameState();
    }

    protected abstract void saveGameState();

    protected abstract void nextInning();


    private void endGame() {
        sendResultToMgr();
    }

    void showFinishGameDialog() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        Fragment prev = fragmentManager.findFragmentByTag(DIALOG_FINISH);
        if (prev != null) {
            fragmentTransaction.remove(prev);
        }
        fragmentTransaction.addToBackStack(null);

        DialogFragment newFragment = EndOfGameDialog.newInstance(homeTeamName, awayTeamName, homeTeamRuns, awayTeamRuns);
        newFragment.setCancelable(false);
        newFragment.show(fragmentTransaction, DIALOG_FINISH);
    }

    private Player getPlayerFromCursor(Uri uri, String playerFirestoreID) {
        if (playerFirestoreID.equals(AUTO_OUT)) {
            return new Player(AUTO_OUT, "(AUTO-OUT)", "xxxx", 3);
        }
        String selection = StatsEntry.COLUMN_FIRESTORE_ID + "=? AND " + StatsEntry.COLUMN_LEAGUE_ID + "=?";
        String[] selectionArgs = {playerFirestoreID, mSelectionID};
        Cursor cursor = getContentResolver().query(uri, null,
                selection, selectionArgs, null);
        cursor.moveToFirst();
        boolean tempData = uri.equals(StatsEntry.CONTENT_URI_TEMP);
        Player player = new Player(cursor, tempData);
        cursor.close();
        return player;
    }

    //sets the textview displays with updated player/game data
    void setDisplays() {
        String playerFirestoreID = currentBatter.getFirestoreID();

        Player inGamePlayer = getPlayerFromCursor(StatsEntry.CONTENT_URI_TEMP, playerFirestoreID);
        String name = inGamePlayer.getName();
        int tHR = inGamePlayer.getHrs();
        int tRBI = inGamePlayer.getRbis();
        int tRun = inGamePlayer.getRuns();
        int t1b = inGamePlayer.getSingles();
        int t2b = inGamePlayer.getDoubles();
        int t3b = inGamePlayer.getTriples();
        int tOuts = inGamePlayer.getOuts();

        Player permanentPlayer = getPlayerFromCursor(StatsEntry.CONTENT_URI_PLAYERS, playerFirestoreID);
        int pRBI = permanentPlayer.getRbis();
        int pRun = permanentPlayer.getRuns();
        int p1b = permanentPlayer.getSingles();
        int p2b = permanentPlayer.getDoubles();
        int p3b = permanentPlayer.getTriples();
        int pHR = permanentPlayer.getHrs();
        int pOuts = permanentPlayer.getOuts();

        int displayHR = tHR + pHR;
        int displayRBI = tRBI + pRBI;
        int displayRun = tRun + pRun;
        int singles = t1b + p1b;
        int doubles = t2b + p2b;
        int triples = t3b + p3b;
        int playerOuts = tOuts + pOuts;
        double avg = calculateAverage(singles, doubles, triples, displayHR, playerOuts);
        String avgString;
        if (Double.isNaN(avg)) {
            avgString = "---";
        } else {
            avgString = formatter.format(avg);
        }

        String nowBattingString = getString(R.string.nowbatting) + " " + name;
        String avgDisplayText = "AVG: " + avgString;
        String hrDisplayText = "HR: " + displayHR;
        String rbiDisplayText = "RBI: " + displayRBI;
        String runDisplayText = "R: " + displayRun;

        batterText.setText(name);
        nowBatting.setText(nowBattingString);
        avgDisplay.setText(avgDisplayText);
        hrDisplay.setText(hrDisplayText);
        rbiDisplay.setText(rbiDisplayText);
        runDisplay.setText(runDisplayText);
        batterDisplay.setVisibility(View.VISIBLE);

        setScoreDisplay();
        setUndoRedo();
    }

    void setScoreDisplay() {
        scoreboardAwayScore.setText(String.valueOf(awayTeamRuns));
        scoreboardHomeScore.setText(String.valueOf(homeTeamRuns));
        setMercyDisplay(inningRuns);
    }

    private void setMercyDisplay(int runs) {
        String mercyRule;
        if (mercyRuns == 99) {
            mercyRule = "OFF";
        } else {
            mercyRule = runs + "/" + mercyRuns;
        }
        String inningRunString = "Mercy\n" + mercyRule;
        mercyDisplay.setText(inningRunString);
        if (runs >= mercyRuns) {
            mercyDisplay.setTextColor(Color.RED);
        } else {
            mercyDisplay.setTextColor(getResources().getColor(R.color.colorHighlight));
        }
    }

    void setInningDisplay() {
        String topOrBottom;
        int scoreboardColor = ContextCompat.getColor(this, R.color.colorScoreboard);
        int atBatColor = ContextCompat.getColor(this, R.color.colorHighlight);
        if (inningNumber % 2 == 0) {
            inningTopArrow.setColorFilter(ContextCompat.getColor(getApplicationContext(), R.color.colorScoreboard));
            inningBottomArrow.setColorFilter(ContextCompat.getColor(getApplicationContext(), R.color.cardview_dark_background));
            topOrBottom = "Top";
            scoreboardAwayName.setTextColor(atBatColor);
            scoreboardAwayScore.setTextColor(atBatColor);
            scoreboardHomeName.setTextColor(scoreboardColor);
            scoreboardHomeScore.setTextColor(scoreboardColor);
            counterDisplay.setText(String.valueOf(countAway));
        } else {
            inningTopArrow.setColorFilter(ContextCompat.getColor(getApplicationContext(), R.color.cardview_dark_background));
            inningBottomArrow.setColorFilter(ContextCompat.getColor(getApplicationContext(), R.color.colorScoreboard));
            topOrBottom = "Bottom";
            scoreboardAwayName.setTextColor(scoreboardColor);
            scoreboardAwayScore.setTextColor(scoreboardColor);
            scoreboardHomeName.setTextColor(atBatColor);
            scoreboardHomeScore.setTextColor(atBatColor);
            counterDisplay.setText(String.valueOf(countHome));
        }
//        mercyDisplay.setTextColor(getResources().getColor(R.color.colorHighlight));
        inningDisplay.setText(String.valueOf(inningNumber / 2));

        String indicator;
        switch (inningNumber / 2) {
            case 1:
                indicator = "st";
                break;
            case 2:
                indicator = "nd";
                break;
            case 3:
                indicator = "rd";
                break;
            default:
                indicator = "th";
        }
        mToastText += "\n " + topOrBottom + " of the " + inningNumber / 2 + indicator;
        toastUpdate(false);
        mToastText = "";
    }

    void updatePlayerStats(String action, int n) {

        String playerFirestoreID;
        if (undoRedo) {
            if (tempBatter == null) {
                return;
            }
            playerFirestoreID = tempBatter;

        } else {
            if (currentBatter == null) {
                return;
            }
            playerFirestoreID = currentBatter.getFirestoreID();
        }

        String selection = StatsEntry.COLUMN_FIRESTORE_ID + "=? AND " + StatsEntry.COLUMN_LEAGUE_ID + "=?";
        String[] selectionArgs = {playerFirestoreID, mSelectionID};
        Cursor cursor = getContentResolver().query(StatsEntry.CONTENT_URI_TEMP,
                null, selection, selectionArgs, null);
        cursor.moveToFirst();

        ContentValues values = new ContentValues();
        int newValue;

        if (action == null) {
            return;
        }
        switch (action) {
            case StatsEntry.COLUMN_1B:
                newValue = StatsContract.getColumnInt(cursor, StatsEntry.COLUMN_1B) + n;
                values.put(StatsEntry.COLUMN_1B, newValue);
                break;
            case StatsEntry.COLUMN_2B:
                newValue = StatsContract.getColumnInt(cursor, StatsEntry.COLUMN_2B) + n;
                values.put(StatsEntry.COLUMN_2B, newValue);
                break;
            case StatsEntry.COLUMN_3B:
                newValue = StatsContract.getColumnInt(cursor, StatsEntry.COLUMN_3B) + n;
                values.put(StatsEntry.COLUMN_3B, newValue);
                break;
            case StatsEntry.COLUMN_HR:
                newValue = StatsContract.getColumnInt(cursor, StatsEntry.COLUMN_HR) + n;
                values.put(StatsEntry.COLUMN_HR, newValue);
                break;
            case StatsEntry.COLUMN_OUT:
            case StatsEntry.COLUMN_FC:
                newValue = StatsContract.getColumnInt(cursor, StatsEntry.COLUMN_OUT) + n;
                values.put(StatsEntry.COLUMN_OUT, newValue);
                break;
            case StatsEntry.COLUMN_ROE:
                newValue = StatsContract.getColumnInt(cursor, StatsEntry.COLUMN_ROE) + n;
                values.put(StatsEntry.COLUMN_ROE, newValue);
                break;
            case StatsEntry.COLUMN_BB:
                newValue = StatsContract.getColumnInt(cursor, StatsEntry.COLUMN_BB) + n;
                values.put(StatsEntry.COLUMN_BB, newValue);
                break;
            case StatsEntry.COLUMN_SF:
                newValue = StatsContract.getColumnInt(cursor, StatsEntry.COLUMN_SF) + n;
                values.put(StatsEntry.COLUMN_SF, newValue);
                break;
            case StatsEntry.COLUMN_K:
                newValue = StatsContract.getColumnInt(cursor, StatsEntry.COLUMN_OUT) + n;
                values.put(StatsEntry.COLUMN_OUT, newValue);
                newValue = StatsContract.getColumnInt(cursor, StatsEntry.COLUMN_K) + n;
                values.put(StatsEntry.COLUMN_K, newValue);
                break;
            case StatsEntry.COLUMN_HBP:
                newValue = StatsContract.getColumnInt(cursor, StatsEntry.COLUMN_HBP) + n;
                values.put(StatsEntry.COLUMN_HBP, newValue);
                break;
            case StatsEntry.COLUMN_SAC_BUNT:
            case StatsEntry.COLUMN_SB:
                break;
            default:
                break;
        }


        StringBuilder stringBuilder = new StringBuilder();
        if (n > 0) {
            stringBuilder.append("+").append(n).append(" ").append(action).append(" ");
        } else if (n < 0) {
            stringBuilder.append("UNDO  \n").append(n).append(" ").append(action).append(" ");
        }

        int rbiCount = currentRunsLog.size();
        if (action.equals(StatsEntry.COLUMN_SB)) {
            stringBuilder.setLength(0);
            if (n < 0) {
                stringBuilder.append("UNDO  \n");
            }
            String[] oldBases;
            String[] newBases = new String[]{firstDisplay.getText().toString(),
                    secondDisplay.getText().toString(), thirdDisplay.getText().toString()};
            if (undoRedo) {
                if (n > 0) {
                    gameCursor.moveToPrevious();
                    BaseLog baseLog = new BaseLog(gameCursor, null, null);
                    gameCursor.moveToNext();
                    oldBases = baseLog.getBasepositions();
                } else {
                    gameCursor.moveToNext();
                    BaseLog baseLog = new BaseLog(gameCursor, null, null);
                    gameCursor.moveToPrevious();
                    oldBases = baseLog.getBasepositions();
                }
            } else {
                oldBases = currentBaseLogStart.getBasepositions();
            }
            for (int i = 0; i < oldBases.length; i++) {
                String oldPlayerOnBase = oldBases[i];
                if (oldPlayerOnBase != null && !oldPlayerOnBase.isEmpty()) {
                    for (int j = 0; j < oldBases.length; j++) {
                        String newPlayerOnBase = newBases[j];
                        if (oldPlayerOnBase.equals(newPlayerOnBase)) {
                            updatePlayerSBs(oldPlayerOnBase, j - i);
                            stringBuilder.append(oldPlayerOnBase).append(" stole ").append(j - i);
                            if (j - i > 1) {
                                stringBuilder.append(" bases \n");
                            } else {
                                stringBuilder.append(" base \n");
                            }
                            break;
                        }
                    }
                    for (String playerScored : currentRunsLog) {
                        if (oldPlayerOnBase.equals(playerScored)) {
                            updatePlayerSBs(oldPlayerOnBase, 3 - i);
                            stringBuilder.append(oldPlayerOnBase).append(" stole ").append(3 - i);
                            if (3 - i > 1) {
                                stringBuilder.append(" bases \n");
                            } else {
                                stringBuilder.append(" base \n");
                            }
                            break;
                        }
                    }
                }
            }
            if (n < 0) {
                String[] oldRunsLog = new String[4];
                gameCursor.moveToNext();
                String run1 = StatsContract.getColumnString(gameCursor, StatsEntry.COLUMN_RUN1);
                String run2 = StatsContract.getColumnString(gameCursor, StatsEntry.COLUMN_RUN2);
                String run3 = StatsContract.getColumnString(gameCursor, StatsEntry.COLUMN_RUN3);
                String run4 = StatsContract.getColumnString(gameCursor, StatsEntry.COLUMN_RUN4);
                gameCursor.moveToPrevious();
                oldRunsLog[0] = run1;
                oldRunsLog[1] = run2;
                oldRunsLog[2] = run3;
                oldRunsLog[3] = run4;

                for (String oldRunnerScored : oldRunsLog) {
                    if (oldRunnerScored != null && !oldRunnerScored.isEmpty()) {
                        for (int j = 0; j < oldBases.length; j++) {
                            String newPlayerOnBase = newBases[j];
                            if (oldRunnerScored.equals(newPlayerOnBase)) {
                                updatePlayerSBs(newPlayerOnBase, j - 3);
                                stringBuilder.append(newPlayerOnBase).append(" stole ").append(3 - j);
                                if (3 - j > 1) {
                                    stringBuilder.append(" bases \n");
                                } else {
                                    stringBuilder.append(" base \n");
                                }
                                break;
                            }
                        }
                    }
                }
            }
        } else if (rbiCount > 0 && !action.equals(StatsEntry.COLUMN_SB)) {
            newValue = StatsContract.getColumnInt(cursor, StatsEntry.COLUMN_RBI) + (rbiCount * n);
            values.put(StatsEntry.COLUMN_RBI, newValue);
        }

        getContentResolver().update(StatsEntry.CONTENT_URI_TEMP, values, selection, selectionArgs);

        if (rbiCount > 0) {
            for (String player : currentRunsLog) {
                updatePlayerRuns(player, n);
                stringBuilder.append("\n").append(player).append(" scored ");
            }
        }
        cursor.close();
        if (tempOuts > 0) {
            stringBuilder.append("\n Team Outs + ").append(tempOuts).append(" ");
        }
        mToastText = stringBuilder.toString();
        toastUpdate(n < 0);
    }


    private void toastUpdate(boolean undoing) {
        if (mToastText == null) {
            return;
        }
        LayoutInflater inflater = getLayoutInflater();
        View layout = inflater.inflate(R.layout.toast_play, (ViewGroup) findViewById(R.id.toast_play_container));
        TextView toastPlayView = layout.findViewById(R.id.toast_play_text);
        if (undoing) {
            toastPlayView.setTextColor(Color.RED);
        }
        toastPlayView.setText(mToastText);
        if (mCurrentToast == null) {
            DisplayMetrics displayMetrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
            int height = displayMetrics.heightPixels;
            int width = displayMetrics.widthPixels;
            mCurrentToast = Toast.makeText(this, mToastText, Toast.LENGTH_SHORT);
            mCurrentToast.setGravity(Gravity.TOP | Gravity.END, width / 30, height * 2 / 7);
        }
        mCurrentToast.setView(layout);
        mCurrentToast.show();
    }

    private void updatePlayerSBs(String player, int n) {

        String selection = StatsEntry.COLUMN_NAME + "=? AND " + StatsEntry.COLUMN_LEAGUE_ID + "=?";
        String[] selectionArgs = {player, mSelectionID};
        Cursor cursor = getContentResolver().query(StatsEntry.CONTENT_URI_TEMP,
                null, selection, selectionArgs, null
        );
        if (cursor.moveToFirst()) {
            ContentValues values = new ContentValues();
            int newValue = StatsContract.getColumnInt(cursor, StatsEntry.COLUMN_SB) + n;
            values.put(StatsEntry.COLUMN_SB, newValue);
            getContentResolver().update(StatsEntry.CONTENT_URI_TEMP, values, selection, selectionArgs);
        }
    }

    protected abstract boolean isTeamAlternate();


    private void updatePlayerRuns(String player, int n) {
        String selection = StatsEntry.COLUMN_NAME + "=? AND " + StatsEntry.COLUMN_LEAGUE_ID + "=?";
        String[] selectionArgs = {player, mSelectionID};
        Cursor cursor = getContentResolver().query(StatsEntry.CONTENT_URI_TEMP, null,
                selection, selectionArgs, null
        );
        if (cursor.moveToFirst()) {
            ContentValues values = new ContentValues();
            int newValue = StatsContract.getColumnInt(cursor, StatsEntry.COLUMN_RUN) + n;
            values.put(StatsEntry.COLUMN_RUN, newValue);
            getContentResolver().update(StatsEntry.CONTENT_URI_TEMP, values, selection, selectionArgs);
        }
        if (!undoRedo) {
            if (inningRuns >= mercyRuns) {
                gameOuts = 3;
                return;
            }
            inningRuns++;
            if (inningRuns >= mercyRuns) {
                gameOuts = 3;
            }
            if (isTopOfInning()) {
                awayTeamRuns++;
            } else if (!isTopOfInning()) {
                homeTeamRuns++;
            }
        }
    }

    private void enableSubmitButton() {
        submitPlay.setEnabled(true);
        submitPlay.getBackground().setAlpha(255);
    }

    private void enableResetButton() {
        resetBasesBtn.setEnabled(true);
        resetBasesBtn.getBackground().setAlpha(255);
    }

    private void disableSubmitButton() {
        submitPlay.setEnabled(false);
        submitPlay.getBackground().setAlpha(64);
    }

    private void disableResetButton() {
        resetBasesBtn.setEnabled(false);
        resetBasesBtn.getBackground().setAlpha(64);
    }

    void resetBases(BaseLog baseLog) {
        String[] bases = baseLog.getBasepositions();
        String first = bases[0];
        String second = bases[1];
        String third = bases[2];
        firstDisplay.setText(first);
        secondDisplay.setText(second);
        thirdDisplay.setText(third);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (first != null && !first.isEmpty()) {
                firstDisplay.setForeground(mRunner);
            } else {
                firstDisplay.setForeground(null);
            }
            if (second != null && !second.isEmpty()) {
                secondDisplay.setForeground(mRunner);
            } else {
                secondDisplay.setForeground(null);
            }
            if (third != null && !third.isEmpty()) {
                thirdDisplay.setForeground(mRunner);
            } else {
                thirdDisplay.setForeground(null);
            }
        }
        batterDisplay.setVisibility(View.VISIBLE);
        batterMoved = false;
        if (!undoRedo) {
            currentRunsLog.clear();
        }
        if (!tempRunsLog.isEmpty()) {
            tempRunsLog.clear();
        }
        disableSubmitButton();
        disableResetButton();
        setBaseListeners();
        tempOuts = 0;
        tempRuns = 0;
        setOutsDisplay(gameOuts);
        setScoreDisplay();
    }

    private void setOutsDisplay(int outs) {
        String outsString = outs + " outs";
        outsDisplay.setText(outsString);
    }

    void emptyBases() {
        firstDisplay.setText(null);
        secondDisplay.setText(null);
        thirdDisplay.setText(null);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            firstDisplay.setForeground(null);
            secondDisplay.setForeground(null);
            thirdDisplay.setForeground(null);
        }
    }

    private double calculateAverage(int singles, int doubles, int triples, int hrs, int outs) {
        double hits = (double) (singles + doubles + triples + hrs);
        return (hits / (outs + hits));
    }

    private void onSubmit() {
        disableSubmitButton();
        if (undoRedo) {
            deleteGameLogs();
            currentRunsLog.clear();
            currentRunsLog.addAll(tempRunsLog);
        }

        if (result == null) {
            Toast.makeText(GameActivity.this, "Please choose a result!", Toast.LENGTH_SHORT).show();
            return;
        }

        updatePlayerStats(result, 1);
        gameOuts += tempOuts;
        if (result.equals(StatsEntry.COLUMN_SB)) {
            nextAfterSB();
            setOutsDisplay(gameOuts);
            return;
        }
        nextBatter();
        setOutsDisplay(gameOuts);
    }

    void deleteGameLogs() {
        String selection = StatsEntry.COLUMN_LEAGUE_ID + "=?";
        String[] selectionArgs = new String[]{mSelectionID};
        gameCursor = getContentResolver().query(StatsEntry.CONTENT_URI_GAMELOG, null,
                selection, selectionArgs, null);
        gameCursor.moveToPosition(gameLogIndex);
        int id = StatsContract.getColumnInt(gameCursor, StatsEntry._ID);
        Uri toDelete = ContentUris.withAppendedId(StatsEntry.CONTENT_URI_GAMELOG, id);
        getContentResolver().delete(toDelete, selection, selectionArgs);
        undoRedo = false;
        redoEndsGame = false;
    }

    protected abstract void undoPlay();

    String getUndoPlayResult() {
        String selection = StatsEntry.COLUMN_LEAGUE_ID + "=?";
        String[] selectionArgs = new String[]{mSelectionID};
        gameCursor = getContentResolver().query(StatsEntry.CONTENT_URI_GAMELOG, null,
                selection, selectionArgs, null);
        gameCursor.moveToPosition(gameLogIndex);
        undoRedo = true;
        tempBatter = StatsContract.getColumnString(gameCursor, StatsEntry.COLUMN_BATTER);
        inningChanged = StatsContract.getColumnInt(gameCursor, StatsEntry.COLUMN_INNING_CHANGED);
        return StatsContract.getColumnString(gameCursor, StatsEntry.COLUMN_PLAY);
    }

    void undoLogs() {
        reloadRunsLog();
        gameCursor.moveToPrevious();
        reloadBaseLog();
        inningRuns = StatsContract.getColumnInt(gameCursor, StatsEntry.COLUMN_INNING_RUNS);
        awayTeamRuns = currentBaseLogStart.getAwayTeamRuns();
        homeTeamRuns = currentBaseLogStart.getHomeTeamRuns();
        if (!tempRunsLog.isEmpty()) {
            tempRunsLog.clear();
        }
        gameOuts = currentBaseLogStart.getOutCount();
        currentBatter = currentBaseLogStart.getBatter();
    }

    protected abstract void redoPlay();

    String getRedoResult() {
        String selection = StatsEntry.COLUMN_LEAGUE_ID + "=?";
        String[] selectionArgs = new String[]{mSelectionID};
        gameCursor = getContentResolver().query(StatsEntry.CONTENT_URI_GAMELOG, null,
                selection, selectionArgs, null);

        if (gameLogIndex < gameCursor.getCount() - 1) {
            undoRedo = true;
            gameLogIndex++;
        } else {
            return null;
        }
        gameCursor.moveToPosition(gameLogIndex);

        reloadRunsLog();
        reloadBaseLog();
        inningRuns = StatsContract.getColumnInt(gameCursor, StatsEntry.COLUMN_INNING_RUNS);
        awayTeamRuns = currentBaseLogStart.getAwayTeamRuns();
        homeTeamRuns = currentBaseLogStart.getHomeTeamRuns();
        gameOuts = currentBaseLogStart.getOutCount();
        currentBatter = currentBaseLogStart.getBatter();
        tempBatter = StatsContract.getColumnString(gameCursor, StatsEntry.COLUMN_BATTER);
        if (!tempRunsLog.isEmpty()) {
            tempRunsLog.clear();
        }
        inningChanged = StatsContract.getColumnInt(gameCursor, StatsEntry.COLUMN_INNING_CHANGED);
        return StatsContract.getColumnString(gameCursor, StatsEntry.COLUMN_PLAY);
    }

    void reloadBaseLog() {
        String batterID = StatsContract.getColumnString(gameCursor, StatsEntry.COLUMN_ONDECK);
        List<Player> teamLineup = getTeamLineup();
        Player batter = findBatterByID(batterID, teamLineup);
        currentBaseLogStart = new BaseLog(gameCursor, batter, teamLineup);
    }

    protected abstract List<Player> getTeamLineup();

    private Player findBatterByID(String batterID, List<Player> teamLineup) {
        for (Player player : teamLineup) {
            if (player.getFirestoreID().equals(batterID)) {
                return player;
            }
        }
        return null;
    }

    void reloadRunsLog() {
        if (currentRunsLog == null) {
            currentRunsLog = new ArrayList<>();
        }
        currentRunsLog.clear();
        String run1 = StatsContract.getColumnString(gameCursor, StatsEntry.COLUMN_RUN1);
        String run2 = StatsContract.getColumnString(gameCursor, StatsEntry.COLUMN_RUN2);
        String run3 = StatsContract.getColumnString(gameCursor, StatsEntry.COLUMN_RUN3);
        String run4 = StatsContract.getColumnString(gameCursor, StatsEntry.COLUMN_RUN4);

        if (run1 == null) {
            return;
        }
        currentRunsLog.add(run1);

        if (run2 == null) {
            return;
        }
        currentRunsLog.add(run2);

        if (run3 == null) {
            return;
        }
        currentRunsLog.add(run3);

        if (run4 == null) {
            return;
        }
        currentRunsLog.add(run4);
    }

    @Override
    public void finishGame(boolean isOver) {
        if (isOver) {
            endGame();
        } else {
            if (!redoEndsGame) {
                updateGameLogs();
                redoEndsGame = true;
            }
            undoPlay();
        }
    }

    @Override
    protected void onDestroy() {
        if (firstDisplay != null) {
            firstDisplay.setOnDragListener(null);
            firstDisplay.setOnTouchListener(null);
        }
        if (secondDisplay != null) {
            secondDisplay.setOnDragListener(null);
            secondDisplay.setOnTouchListener(null);
        }
        if (thirdDisplay != null) {
            thirdDisplay.setOnDragListener(null);
            thirdDisplay.setOnTouchListener(null);
        }
        if (homeDisplay != null) {
            homeDisplay.setOnDragListener(null);
            homeDisplay.setOnTouchListener(null);
        }
        if (outTrash != null) {
            outTrash.setOnDragListener(null);
        }
        if (batterDisplay != null) {
            batterDisplay.setOnTouchListener(null);
        }
        super.onDestroy();
    }

    private void addTempOuts(){
        tempOuts++;
        int sumOuts = gameOuts + tempOuts;
        setOutsDisplay(sumOuts);
    }

    protected abstract boolean isTopOfInning();

    class MyDragListener implements View.OnDragListener {

        @Override
        public boolean onDrag(View v, final DragEvent event) {
            final View eventView = (View) event.getLocalState();

            int action = event.getAction();
            switch (action) {
                case DragEvent.ACTION_DRAG_STARTED:
                    if (eventView == null) {
                        mResetListeners = true;
                        return false;
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        switch (v.getId()) {
                            case R.id.home_display:
                                v.setBackground(getDrawable(R.drawable.img_home2));
                                break;
                            case R.id.trash:
                                v.setBackground(getDrawable(R.drawable.img_base2));
                                break;
                            default:
                                v.setBackground(getDrawable(R.drawable.img_base2));
                                break;
                        }
                    }
                    break;
                case DragEvent.ACTION_DROP:
                    if (eventView == null) {
                        return false;
                    }
                    TextView dropPoint = null;
                    if (v.getId() != R.id.trash) {
                        dropPoint = (TextView) v;
                    }
                    ClipData.Item item = event.getClipData().getItemAt(0);
                    String nameString = item.getText().toString();

                    batterDisplay.setOnTouchListener(null);
                    firstDisplay.setOnTouchListener(null);
                    secondDisplay.setOnTouchListener(null);
                    thirdDisplay.setOnTouchListener(null);
                    homeDisplay.setOnTouchListener(null);

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        switch (v.getId()) {
                            case R.id.home_display:
                                v.setBackground(getDrawable(R.drawable.img_home));
                                break;
                            case R.id.trash:
                                v.setBackgroundResource(0);
                                break;
                            default:
                                v.setBackground(getDrawable(R.drawable.img_base));
                                break;
                        }
                    }
                    if (v.getId() == R.id.trash) {
                        if (eventView instanceof TextView) {
                            TextView draggedView = (TextView) eventView;
                            draggedView.setText(null);
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                draggedView.setForeground(null);
                            }
                        } else {
                            setBatterDropped();
                        }
                        addTempOuts();
                    } else {
                        if (dropPoint == null) {
                            return false;
                        }
                        if (eventView instanceof TextView) {
                            ((TextView) eventView).setText(null);
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                eventView.setForeground(null);
                            }
                        } else {
                            setBatterDropped();
                        }
                        dropPoint.setText(nameString);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            dropPoint.setForeground(mRunner);
                        }

                        if (dropPoint == homeDisplay) {
                            homeDisplay.bringToFront();
                            if (undoRedo) {
                                tempRunsLog.add(nameString);
                            } else {
                                currentRunsLog.add(nameString);
                            }

                            homeDisplay.setText(null);
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                homeDisplay.setForeground(null);
                            }
                            tempRuns++;

                            String scoreString;
                            if (inningRuns + tempRuns <= mercyRuns) {
                                if (isTopOfInning()) {
                                    scoreString = String.valueOf(awayTeamRuns + tempRuns);
                                    scoreboardAwayScore.setText(scoreString);
                                } else {
                                    scoreString = String.valueOf(homeTeamRuns + tempRuns);
                                    scoreboardHomeScore.setText(scoreString);
                                }
                            }
                            setMercyDisplay(inningRuns + tempRuns);
                        }
                    }
                    enableResetButton();
                    mResetListeners = true;
                    mResetDrag = false;
                    break;
                case DragEvent.ACTION_DRAG_ENDED:
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        switch (v.getId()) {
                            case R.id.home_display:
                                v.setBackground(getDrawable(R.drawable.img_home));
                                break;
                            case R.id.trash:
                                v.setBackgroundResource(0);
                                break;
                            default:
                                v.setBackground(getDrawable(R.drawable.img_base));
                        }
                    }
                    if (mResetListeners) {
                        mResetListeners = false;
                        setBaseListeners();
                    }
                    if (eventView == null) {
                        return false;
                    }
                    if (mResetDrag) {
                        mResetDrag = false;
                        if (eventView instanceof TextView && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            eventView.setForeground(mRunner);
                        }
                        eventView.setAlpha(1f);
                    }
                    break;
            }
            return true;
        }
    }

    private void setBatterDropped() {
        batterDisplay.setVisibility(View.INVISIBLE);
        batterMoved = true;
        if (playEntered) {
            if (result.equals(StatsEntry.COLUMN_SB)) {
                cancelSBClick();
            } else {
                enableSubmitButton();
            }
        }
    }

    private void cancelSBClick() {
        result = null;
        playEntered = false;
        group2.clearCheck();
        disableSubmitButton();
    }

    final class MyTouchListener implements View.OnTouchListener {

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                setBaseListeners();

                TextView tv;
                String nameString;
                switch (view.getId()) {
                    case R.id.batter:
                        view.setAlpha(.1f);
                        nameString = currentBatter.getName();

                        if (firstOccupied) {
                            secondDisplay.setOnDragListener(null);
                            thirdDisplay.setOnDragListener(null);
                            homeDisplay.setOnDragListener(null);
                        } else if (secondOccupied) {
                            thirdDisplay.setOnDragListener(null);
                            homeDisplay.setOnDragListener(null);
                        } else if (thirdOccupied) {
                            homeDisplay.setOnDragListener(null);
                        }
                        break;
                    case R.id.first_display:
                        tv = (TextView) view;
                        nameString = tv.getText().toString();
                        view.setAlpha(.5f);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            tv.setForeground(null);
                        }
                        if (secondOccupied) {
                            thirdDisplay.setOnDragListener(null);
                            homeDisplay.setOnDragListener(null);
                        } else if (thirdOccupied) {
                            homeDisplay.setOnDragListener(null);
                        }
                        break;
                    case R.id.second_display:
                        tv = (TextView) view;
                        nameString = tv.getText().toString();
                        view.setAlpha(.5f);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            tv.setForeground(null);
                        }
                        firstDisplay.setOnDragListener(null);
                        if (thirdOccupied) {
                            homeDisplay.setOnDragListener(null);
                        }
                        break;
                    case R.id.third_display:
                        tv = (TextView) view;
                        nameString = tv.getText().toString();
                        view.setAlpha(.5f);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            tv.setForeground(null);
                        }
                        firstDisplay.setOnDragListener(null);
                        secondDisplay.setOnDragListener(null);
                        break;
                    default:
                        return false;
                }

                if (nameString.isEmpty()) {
                    Toast.makeText(GameActivity.this, "Error", Toast.LENGTH_SHORT).show();
                    view.setAlpha(1f);
                    return false;
                }
                view.setTag(nameString);
                ClipData data = ClipData.newPlainText("baserunner", nameString);
                View.DragShadowBuilder shadowBuilder = new MyDragShadowBuilder(view);
                mResetDrag = true;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    view.startDragAndDrop(data, shadowBuilder, view, 0);
                } else {
                    view.startDrag(data, shadowBuilder, view, 0);
                }
            }
            view.performClick();
            return true;
        }
    }

    class MyDragShadowBuilder extends View.DragShadowBuilder {

        MyDragShadowBuilder(View view) {
            super(view);
        }

        @Override
        public void onProvideShadowMetrics(Point outShadowSize, Point outShadowTouchPoint) {
            super.onProvideShadowMetrics(outShadowSize, outShadowTouchPoint);
        }

        @Override
        public void onDrawShadow(Canvas canvas) {
            String playerText = getView().getTag().toString();
            getView().setTag(null);
            int width = getView().getWidth();
            int height = getView().getHeight();
            int xPos = (canvas.getWidth() / 2);
            int textSize = width / 8;

            Resources res = getResources();
            Bitmap b = BitmapFactory.decodeResource(res, R.drawable.ic_directions_run_black_18dp);
            Bitmap resizedB = Bitmap.createScaledBitmap(b, width, height, false);
            canvas.drawBitmap(resizedB, 0, 0, null);

            Paint paint = new Paint();
            paint.setARGB(255, 255, 255, 255);
            float textWidth = paint.measureText(playerText) * width / 90;
            RectF rectF = new RectF((xPos - (textWidth / 2) - (width / 27)), height - (width * 2 / 9), (xPos + (textWidth / 2) + (width / 27)), (height - (width / 54)));
            canvas.drawRoundRect(rectF, 20.0f, 20.0f, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(width / 54);
            paint.setColor(getResources().getColor(R.color.colorPrimary));
            canvas.drawRoundRect(rectF, 20.0f, 20.0f, paint);

            Paint textPaint = new Paint();
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setARGB(255, 0, 0, 0);
            textPaint.setColor(getResources().getColor(R.color.colorPrimaryDark));
            textPaint.setTextSize(textSize);

            canvas.drawText(playerText, xPos, height - (width / 18), textPaint);
        }
    }

    protected abstract void increaseLineupIndex();

    protected abstract void decreaseLineupIndex();

    protected abstract void checkLineupIndex();

    protected int setLineupIndex(List<Player> team, String playerFirestoreID) {
        for (int i = 0; i < team.size(); i++) {
            Player player = team.get(i);
            if (player.getFirestoreID().equals(playerFirestoreID)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_game, menu);
        return true;
    }

    protected abstract void gotoLineupEditor(String teamName, String teamID);

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_undo_play:
                undoPlay();
                break;
            case R.id.action_redo_play:
                redoPlay();
                break;
            case R.id.action_add_out:
                addTempOuts();
                break;
            case R.id.action_edit_lineup:
                openEditWarningDialog();
                break;
            case R.id.action_off_lineup_rules:
                actionEndLineupRules();
                break;
            case R.id.action_goto_stats:
                actionViewBoxScore();
                break;
            case R.id.change_game_settings:
                openGameSettingsDialog();
                break;
            case R.id.action_exit_game:
                showExitDialog();
                break;
            case R.id.action_finish_game:
                showFinishConfirmationDialog();
                break;
            case R.id.action_set_SB:
                setSB(item);
                break;
            case R.id.action_edit_score:
                openAddRunsDialog();
                break;
            case R.id.show_counter:
                showCounter();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setSB(MenuItem item) {
        RadioButton sbBtn = findViewById(R.id.sb_rb);
        RadioButton kBtn = findViewById(R.id.k_rb);
        RadioButton hbpBtn = findViewById(R.id.hbp_rb);
        SharedPreferences sharedPreferences = getSharedPreferences(mSelectionID + StatsEntry.SETTINGS, Context.MODE_PRIVATE);
        boolean sbOn = !sharedPreferences.getBoolean(StatsEntry.COLUMN_SB, false);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(StatsEntry.COLUMN_SB, sbOn);
        editor.apply();
        if (sbOn) {
            sbBtn.setVisibility(View.VISIBLE);
            kBtn.setVisibility(View.VISIBLE);
            hbpBtn.setVisibility(View.VISIBLE);
            item.setTitle(R.string.stolen_bases_on);
        } else {
            sbBtn.setVisibility(View.GONE);
            kBtn.setVisibility(View.GONE);
            hbpBtn.setVisibility(View.GONE);
            item.setTitle(R.string.stolen_bases_off);
        }
    }

    private void openGameSettingsDialog() {
        int genderSorter = 0;
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        SharedPreferences settingsPreferences = getSharedPreferences(mSelectionID + StatsEntry.SETTINGS, Context.MODE_PRIVATE);
        boolean extraGirlsSorted = settingsPreferences.getBoolean(StatsEntry.SORT_GIRLS, false);
        DialogFragment newFragment = GameSettingsDialog.newInstance(totalInnings, genderSorter, mercyRuns, mSelectionID, inningNumber, gameHelp, extraGirlsSorted);
        newFragment.show(fragmentTransaction, "");
    }

    private void openAddRunsDialog() {
        int awayR;
        int homeR;
        if (isTopOfInning()) {
            awayR = awayTeamRuns + tempRuns;
            homeR = homeTeamRuns;
        } else {
            awayR = awayTeamRuns;
            homeR = homeTeamRuns + tempRuns;
        }
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        DialogFragment newFragment = AddRunsDialog.newInstance(awayR, homeR);
        newFragment.show(fragmentTransaction, "");
    }

    private void actionViewBoxScore() {
        Intent statsIntent = new Intent(GameActivity.this, BoxScoreActivity.class);
        Bundle b = getBoxScoreBundle();
        statsIntent.putExtras(b);
        startActivity(statsIntent);
    }

    protected abstract Bundle getBoxScoreBundle();

    private void actionEndLineupRules() {
        if (currentBatter != null && currentBatter.getFirestoreID().equals(AUTO_OUT)) {
            Toast.makeText(GameActivity.this, "Can't reset lineup rules during Auto-Out", Toast.LENGTH_LONG).show();
            return;
        }
        revertLineups();
        lowestIndex = gameLogIndex;
        highestIndex = gameLogIndex;
        setUndoRedo();
        SharedPreferences gamePreferences = getSharedPreferences(mSelectionID + StatsEntry.GAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = gamePreferences.edit();
        editor.putInt(KEY_GENDERSORT, 0);
        editor.apply();
        invalidateOptionsMenu();
    }

    protected abstract void revertLineups();

    private void showExitDialog() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        DialogFragment newFragment = SaveDeleteGameDialog.newInstance();
        newFragment.show(fragmentTransaction, "");
    }

    @Override
    public void exitGameChoice(boolean save) {
        if (!save) {
            deleteTempData();
            SharedPreferences savedGamePreferences = getSharedPreferences(mSelectionID + StatsEntry.GAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = savedGamePreferences.edit();
            editor.clear();
            editor.apply();
        }
        finish();
    }

    private void deleteTempData() {
        String selection = StatsEntry.COLUMN_LEAGUE_ID + "=?";
        String[] selectionArgs = new String[]{mSelectionID};
        getContentResolver().delete(StatsEntry.CONTENT_URI_GAMELOG, selection, selectionArgs);
        getContentResolver().delete(StatsEntry.CONTENT_URI_TEMP, selection, selectionArgs);
    }

    protected abstract void sendResultToMgr();

    protected abstract void actionEditLineup();

    void setUndoRedo() {
        setUndoButton();
        setRedoButton();
    }

    private void setUndoButton() {
        boolean undo = gameLogIndex > lowestIndex;
        undoButton.setEnabled(undo);
        if (undo) {
            undoButton.setAlpha(1f);
        } else {
            undoButton.setAlpha(.1f);
        }
    }

    private void setRedoButton() {
        boolean redo = gameLogIndex < highestIndex;
        redoButton.setEnabled(redo);
        if (redo) {
            redoButton.setAlpha(1f);
        } else {
            redoButton.setAlpha(.1f);
        }
    }

    //warning dialog

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem undoItem = menu.findItem(R.id.action_undo_play);
        MenuItem redoItem = menu.findItem(R.id.action_redo_play);
        MenuItem resetLineupItem = menu.findItem(R.id.action_off_lineup_rules);
        MenuItem sbItem = menu.findItem(R.id.action_set_SB);

        SharedPreferences sharedPreferences = getSharedPreferences(mSelectionID + StatsEntry.SETTINGS, Context.MODE_PRIVATE);
        boolean sbOn = sharedPreferences.getBoolean(StatsEntry.COLUMN_SB, false);
        if (sbOn) {
            sbItem.setTitle(R.string.stolen_bases_on);
        } else {
            sbItem.setTitle(R.string.stolen_bases_off);
        }

        boolean undo = gameLogIndex > lowestIndex;
        boolean redo = gameLogIndex < highestIndex;

        undoItem.setVisible(undo);
        redoItem.setVisible(redo);

        SharedPreferences gamePreferences = getSharedPreferences(mSelectionID + StatsEntry.GAME, MODE_PRIVATE);
        int sortArg = gamePreferences.getInt(KEY_GENDERSORT, 0);
        if (sortArg == 0) {
            resetLineupItem.setVisible(false);
        } else {
            resetLineupItem.setVisible(true);
        }

        return true;
    }

    private void showFinishConfirmationDialog() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        DialogFragment newFragment = FinishGameConfirmationDialog.newInstance();
        newFragment.setCancelable(false);
        newFragment.show(fragmentTransaction, "");
    }

    @Override
    public void finishEarly() {
        if (undoRedo) {
            deleteGameLogs();
        }
        endGame();
    }


    String getTeamNameFromFirestoreID(String firestoreID) {
        String selection = StatsEntry.COLUMN_FIRESTORE_ID + "=? AND " + StatsEntry.COLUMN_LEAGUE_ID + "=?";
        String[] selectionArgs = new String[]{firestoreID, mSelectionID};
        String[] projection = new String[]{StatsEntry.COLUMN_NAME};

        Cursor cursor = getContentResolver().query(StatsEntry.CONTENT_URI_TEAMS,
                projection, selection, selectionArgs, null);
        String name = null;
        if (cursor.moveToFirst()) {
            name = StatsContract.getColumnString(cursor, StatsEntry.COLUMN_NAME);
        }
        cursor.close();
        return name;
    }

    @Override
    public void onGameSettingsChanged(int innings, int genderSorter, int mercy) {
        totalInnings = innings;
        mercyRuns = mercy;
        SharedPreferences gamePreferences = getSharedPreferences(mSelectionID + StatsEntry.GAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = gamePreferences.edit();
        editor.putInt(KEY_TOTALINNINGS, totalInnings);
        editor.putInt(StatsEntry.MERCY, mercyRuns);
        editor.apply();

        if (inningNumber / 2 >= totalInnings) {
            finalInning = true;
        } else {
            finalInning = false;
            redoEndsGame = false;
        }
        setScoreDisplay();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_EDIT) {
            String selection = StatsEntry.COLUMN_LEAGUE_ID + "=?";
            String[] selectionArgs = new String[]{mSelectionID};
            gameCursor = getContentResolver().query(StatsEntry.CONTENT_URI_GAMELOG, null,
                    selection, selectionArgs, null);
            gameCursor.moveToFirst();
            if (resultCode == RESULT_CODE_EDITED) {
                lowestIndex = gameLogIndex;
                SharedPreferences gamePreferences = getSharedPreferences(mSelectionID + StatsEntry.GAME, MODE_PRIVATE);
                SharedPreferences.Editor editor = gamePreferences.edit();
                editor.putInt(KEY_LOWESTINDEX, lowestIndex);
                editor.apply();
                if (!getSelectionData()) {
                    goToMain();
                    return;
                }
                setCustomViews();
                setViews();
                loadGamePreferences();
                checkLineupIndex();
                if (undoRedo) {
                    deleteGameLogs();
                    highestIndex = gameLogIndex;
                    invalidateOptionsMenu();
                    setUndoRedo();
                }
                resumeGame();
                Toast.makeText(GameActivity.this, "Lineups have been edited.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    void setFinalInning() {
        finalInning = inningNumber / 2 >= totalInnings;
    }

    protected abstract void openEditWarningDialog();

    @Override
    public void onEditConfirmed() {
        actionEditLineup();
    }

    @Override
    public void onChangeRuns(int awayR, int homeR) {
        if (isTopOfInning()) {
            awayTeamRuns = awayR - tempRuns;
            homeTeamRuns = homeR;
        } else {
            awayTeamRuns = awayR;
            homeTeamRuns = homeR - tempRuns;
        }
        scoreboardAwayScore.setText(String.valueOf(awayR));
        scoreboardHomeScore.setText(String.valueOf(homeR));
    }

    private void showCounter() {
        View counterLayout = findViewById(R.id.counter_layout);
        if(counterLayout.getVisibility() == View.GONE) {
            counterLayout.setVisibility(View.VISIBLE);
        } else {
            counterLayout.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("countAway", countAway);
        outState.putInt("countHome", countHome);
    }
}