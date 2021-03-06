package xyz.sleekstats.softball.fragments;


import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.util.Pair;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import xyz.sleekstats.softball.R;
import xyz.sleekstats.softball.activities.BoxScoreActivity;
import xyz.sleekstats.softball.activities.GameActivity;
import xyz.sleekstats.softball.activities.MainActivity;
import xyz.sleekstats.softball.activities.TeamGameActivity;
import xyz.sleekstats.softball.activities.TeamManagerActivity;
import xyz.sleekstats.softball.activities.UsersActivity;
import xyz.sleekstats.softball.adapters.MyLineupAdapter;
import xyz.sleekstats.softball.data.StatsContract;
import xyz.sleekstats.softball.data.StatsContract.StatsEntry;
import xyz.sleekstats.softball.dialogs.AddNewPlayersDialog;
import xyz.sleekstats.softball.dialogs.GameSettingsDialog;
import xyz.sleekstats.softball.dialogs.LineupSortDialog;
import xyz.sleekstats.softball.models.MainPageSelection;
import xyz.sleekstats.softball.models.Player;

import com.woxthebox.draglistview.BoardView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LineupFragment extends Fragment {

    private BoardView mBoardView;
    private int sCreatedItems;
    private int mColumnWidth;

    private OnFragmentInteractionListener mListener;
    private boolean sortLineup;

    private TextView gameSummaryView;
    private TextView inningsView;
    private TextView orderView;
    private Button lineupSubmitButton;

    private List<Player> mLineup;
    private List<Player> mBench;
    private String mTeamName;
    private String mTeamID;
    private int mType;
    private String mSelectionID;
    private boolean inGame;
    private boolean postGameUpdate;

    private static final String KEY_TEAM_NAME = "team_name";
    private static final String KEY_TEAM_ID = "team_id";
    private static final String KEY_INGAME = "ingame";
    private static final int LINEUP_INDEX = 0;
    private static final int BENCH_INDEX = 1;

    public LineupFragment() {
        // Required empty public constructor
    }

    public static LineupFragment newInstance(String selectionID, int selectionType,
                                             String teamName, String teamID, boolean isInGame) {
        Bundle args = new Bundle();
        args.putString(MainPageSelection.KEY_SELECTION_ID, selectionID);
        args.putInt(MainPageSelection.KEY_SELECTION_TYPE, selectionType);
        args.putString(KEY_TEAM_NAME, teamName);
        args.putString(KEY_TEAM_ID, teamID);
        args.putBoolean(KEY_INGAME, isInGame);
        LineupFragment fragment = new LineupFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mColumnWidth = 0;
        setHasOptionsMenu(true);
        Bundle args = getArguments();
        if (args != null) {
            mSelectionID = args.getString(MainPageSelection.KEY_SELECTION_ID);
            mType = args.getInt(MainPageSelection.KEY_SELECTION_TYPE);
            mTeamName = args.getString(KEY_TEAM_NAME);
            mTeamID = args.getString(KEY_TEAM_ID);
            inGame = args.getBoolean(KEY_INGAME);
        } else {
            requireActivity().finish();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final View rootView = inflater.inflate(R.layout.fragment_lineup, container, false);

        lineupSubmitButton = rootView.findViewById(R.id.lineup_submit);
        lineupSubmitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                lineupSubmitButton.setEnabled(false);
                if (inGame) {
                    onSubmitEdit();
                } else {
                    onSubmitLineup();
                }
            }
        });

        if (inGame) {
            lineupSubmitButton.setText(R.string.save_edit);
        }

        final TextView teamNameTextView = rootView.findViewById(R.id.team_name_display);
        teamNameTextView.setText(mTeamName);

        final FloatingActionButton addPlayersButton = rootView.findViewById(R.id.btn_start_adder);
        addPlayersButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                createTeamFragment(mTeamName, mTeamID);
            }
        });

        final FloatingActionButton shuffleButton = rootView.findViewById(R.id.btn_randomize);
        shuffleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                shuffleBoardView();
            }
        });

        final FloatingActionButton clearButton = rootView.findViewById(R.id.btn_clear);
        clearButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                clearLineupBoardView();
            }
        });

        mBoardView = rootView.findViewById(R.id.team_bv);
        mBoardView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                mBoardView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                mColumnWidth = mBoardView.getWidth(); //height is ready
                mBoardView.setColumnWidth(mColumnWidth / 2);

                if (mLineup == null) {
                    mLineup = new ArrayList<>();
                } else {
                    mLineup.clear();
                }

                if (mBench == null) {
                    mBench = new ArrayList<>();
                } else {
                    mBench.clear();
                }

                String selection = StatsEntry.COLUMN_TEAM_FIRESTORE_ID + "=? AND " + StatsEntry.COLUMN_LEAGUE_ID + "=?";
                String[] selectionArgs = new String[]{mTeamID, mSelectionID};
                String sortOrder = StatsEntry.COLUMN_ORDER + " ASC";
                Cursor cursor = requireActivity().getContentResolver().query(StatsEntry.CONTENT_URI_PLAYERS,
                        null, selection, selectionArgs, sortOrder);

                while (cursor.moveToNext()) {
                    Player player = new Player(cursor, false);
                    int playerOrder = StatsContract.getColumnInt(cursor, StatsEntry.COLUMN_ORDER);
                    if (playerOrder > 50) {
                        mBench.add(player);
                    } else {
                        mLineup.add(player);
                    }
                }
                cursor.close();
                startBoardView();
            }
        });

        mBoardView.setSnapToColumnsWhenScrolling(true);
        mBoardView.setSnapToColumnWhenDragging(true);
        mBoardView.setSnapDragItemToTouch(true);
//        mBoardView.setCustomDragItem(new MyDragItem(getActivity(), R.layout.column_item));
        mBoardView.setSnapToColumnInLandscape(false);
        mBoardView.setColumnSnapPosition(BoardView.ColumnSnapPosition.CENTER);
        mBoardView.setBoardListener(new BoardView.BoardListener() {
            @Override
            public void onItemDragStarted(int column, int row) {
            }

            @Override
            public void onItemChangedPosition(int oldColumn, int oldRow, int newColumn, int newRow) {
            }

            @Override
            public void onItemChangedColumn(int oldColumn, int newColumn) {
            }

            @Override
            public void onFocusedColumnChanged(int oldColumn, int newColumn) {
            }

            @Override
            public void onColumnDragStarted(int position) {
            }

            @Override
            public void onColumnDragChangedPosition(int oldPosition, int newPosition) {
            }

            @Override
            public void onColumnDragEnded(int position) {
            }

            @Override
            public void onItemDragEnded(int fromColumn, int fromRow, int toColumn, int toRow) {
            }
        });
        mBoardView.setBoardCallback(new BoardView.BoardCallback() {
            @Override
            public boolean canDragItemAtPosition(int column, int dragPosition) {
                return true;
            }

            @Override
            public boolean canDropItemAtPosition(int oldColumn, int oldRow, int newColumn, int newRow) {
                return true;
            }
        });

        return rootView;
    }


    public void setPostGameLayout(boolean clickable) {
        postGameUpdate = !clickable;
        View view = getView();
        if (view == null) {
            return;
        }
        view.findViewById(R.id.lineup_submit).setEnabled(clickable);
        view.findViewById(R.id.continue_game).setVisibility(View.GONE);
        if (gameSummaryView == null) {
            gameSummaryView = view.findViewById(R.id.current_game_view);
        }
        gameSummaryView.setVisibility(View.GONE);
    }

    public void onTransferError() {
        postGameUpdate = false;
        View view = getView();
        if (view == null) {
            return;
        }
        view.findViewById(R.id.lineup_submit).setEnabled(true);
        view.findViewById(R.id.continue_game).setVisibility(View.VISIBLE);
        if (gameSummaryView == null) {
            gameSummaryView = view.findViewById(R.id.current_game_view);
        }
        gameSummaryView.setVisibility(View.VISIBLE);
    }

    private void resetBoardView() {
        if (mBoardView == null) {
            return;
        }
        mBoardView.clearBoard();
        sCreatedItems = 0;
        startBoardView();
    }

    private void shuffleBoardView() {
        if (mLineup == null) {
            mLineup = new ArrayList<>();
        } else {
            mLineup.clear();
        }
        if (mBench == null) {
            mBench = new ArrayList<>();
        } else {
            mBench.clear();
        }
        List lineupList = mBoardView.getAdapter(LINEUP_INDEX).getItemList();
        for (Object playerObject : lineupList) {
            Pair<Long, Player> pair = (Pair<Long, Player>) playerObject;
            Player player = pair.second;
            mLineup.add(player);
        }
        List benchList = mBoardView.getAdapter(BENCH_INDEX).getItemList();
        for (Object playerObject : benchList) {
            Pair<Long, Player> pair = (Pair<Long, Player>) playerObject;
            Player player = pair.second;
            mBench.add(player);
        }
        if (mLineup != null) {
            Collections.shuffle(mLineup);
        }
        resetBoardView();
    }

    private void clearLineupBoardView() {
        mBench.addAll(mLineup);
        mLineup.clear();
        resetBoardView();
    }

    private void startBoardView() {
        addColumnList(mLineup, false);
        addColumnList(mBench, true);
    }

    private void addColumnList(List<Player> playerList, boolean isBench) {
        final ArrayList<Pair<Long, Player>> mItemArray = new ArrayList<>();
        for (Player player : playerList) {
            long id = sCreatedItems++;
            mItemArray.add(new Pair<>(id, player));
        }
        final MyLineupAdapter listAdapter = new MyLineupAdapter(mItemArray, getActivity(), isBench, getGenderSorter());
        mBoardView.addColumn(listAdapter, null, null, false);
    }

    private void onSubmitEdit() {
        if (isLineupOK()) {
            setNewLineupToTempDB(getPreviousLineup(mTeamID));
            SharedPreferences gamePreferences = requireActivity().getSharedPreferences(mSelectionID + StatsEntry.GAME, Context.MODE_PRIVATE);

            SharedPreferences.Editor editor = gamePreferences.edit();
            if (mType == MainPageSelection.TYPE_LEAGUE) {
                String awayTeam = gamePreferences.getString(StatsEntry.COLUMN_AWAY_TEAM, null);
                String homeTeam = gamePreferences.getString(StatsEntry.COLUMN_HOME_TEAM, null);
                int sortArgument = gamePreferences.getInt(GameActivity.KEY_GENDERSORT, 0);

                switch (sortArgument) {
                    case 3:
                        if (mTeamID.equals(awayTeam)) {
                            sortArgument = 2;
                        } else if (mTeamID.equals(homeTeam)) {
                            sortArgument = 1;
                        }
                        break;

                    case 2:
                        if (mTeamID.equals(homeTeam)) {
                            sortArgument = 0;
                        }
                        break;

                    case 1:
                        if (mTeamID.equals(awayTeam)) {
                            sortArgument = 0;
                        }
                        break;
                }
                editor.putInt(GameActivity.KEY_GENDERSORT, sortArgument);
            } else {
                editor.putInt(GameActivity.KEY_GENDERSORT, 0);
            }
            editor.apply();
            requireActivity().setResult(GameActivity.RESULT_CODE_EDITED);
//            intent.putExtra("edited", true);
//            startActivity(intent);
            requireActivity().finish();
        } else {
            Toast.makeText(getActivity(), "Add more players to lineup first.", Toast.LENGTH_SHORT).show();
            lineupSubmitButton.setEnabled(true);
        }
    }

    private void onSubmitLineup() {
        if (mType == MainPageSelection.TYPE_TEAM) {
            int genderSorter = getGenderSorter();

            if (isLineupOK()) {
                final Button continueGameButton = getView().findViewById(R.id.continue_game);
                continueGameButton.setVisibility(View.GONE);
                gameSummaryView.setVisibility(View.GONE);
                clearGameDB();
                boolean lineupCheck = addTeamToTempDB(genderSorter);
                if (lineupCheck) {
                    startGame(isHome());
                } else {
                    lineupSubmitButton.setEnabled(true);
                }
            } else {
                lineupSubmitButton.setEnabled(true);
                Toast.makeText(getActivity(), "Add more players to lineup first.",
                        Toast.LENGTH_SHORT).show();
            }
        } else {
            updateAndSubmitLineup();
            requireActivity().finish();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        final Button continueGameButton = getView().findViewById(R.id.continue_game);
        gameSummaryView = getView().findViewById(R.id.current_game_view);
        inningsView = getView().findViewById(R.id.innings_view);
        View radioButtonGroup = getView().findViewById(R.id.radiobtns_away_or_home_team);
        orderView = getView().findViewById(R.id.gender_lineup_view);
        final LinearLayout settingsLayout = getView().findViewById(R.id.layout_settings);
        settingsLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                settingsLayout.setEnabled(false);
                openGameSettingsDialog();
                settingsLayout.setEnabled(true);
            }
        });
        if (lineupSubmitButton == null) {
            lineupSubmitButton = getView().findViewById(R.id.lineup_submit);
        }
        lineupSubmitButton.setEnabled(true);
        if (mType == MainPageSelection.TYPE_TEAM && !inGame) {
            SharedPreferences settingsPreferences = requireActivity()
                    .getSharedPreferences(mSelectionID + StatsEntry.SETTINGS, Context.MODE_PRIVATE);
            final int innings = settingsPreferences.getInt(StatsEntry.INNINGS, 7);
            final int genderSorter = settingsPreferences.getInt(StatsEntry.COLUMN_GENDER, 0);
            settingsLayout.setVisibility(View.VISIBLE);
            inningsView.setVisibility(View.VISIBLE);
            setGameSettings(innings, genderSorter);


            lineupSubmitButton.setText(R.string.start);
            radioButtonGroup.setVisibility(View.VISIBLE);

            continueGameButton.setEnabled(true);
            continueGameButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    continueGameButton.setEnabled(false);
                    if (lineupSubmitButton != null) {
                        lineupSubmitButton.setEnabled(false);
                    }
                    Intent intent = new Intent(getActivity(), TeamGameActivity.class);
                    if (mListener != null) {
                        mListener.startGameActivity(intent);
                    }
                }
            });
//            continueGameButton.setVisibility(View.VISIBLE);

            String selection = StatsEntry.COLUMN_LEAGUE_ID + "=?";
            String[] selectionArgs = new String[]{mSelectionID};
            Cursor cursor = requireActivity().getContentResolver().query(StatsEntry.CONTENT_URI_GAMELOG,
                    null, selection, selectionArgs, null);
            if (!postGameUpdate && cursor.moveToLast()) {
                int awayRuns = StatsContract.getColumnInt(cursor, StatsEntry.COLUMN_AWAY_RUNS);
                int homeRuns = StatsContract.getColumnInt(cursor, StatsEntry.COLUMN_HOME_RUNS);
                setGameSummaryView(awayRuns, homeRuns);
                continueGameButton.setVisibility(View.VISIBLE);
                gameSummaryView.setVisibility(View.VISIBLE);
            } else {
                continueGameButton.setVisibility(View.GONE);
                gameSummaryView.setVisibility(View.INVISIBLE);
            }
            cursor.close();
        } else {
            settingsLayout.setVisibility(View.GONE);
            continueGameButton.setVisibility(View.GONE);
            gameSummaryView.setVisibility(View.GONE);
            radioButtonGroup.setVisibility(View.GONE);
        }
    }

    public void updateBench(List<Player> players) {
        for (Player player : players) {
            long id = sCreatedItems++;
            Pair<Long, Player> pair = new Pair<>(id, player);
            mBench.add(player);
            mBoardView.addItem(BENCH_INDEX, 0, pair, true);
        }
    }

    private void createTeamFragment(String teamName, String teamID) {
        FragmentManager fragmentManager = requireActivity().getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        DialogFragment newFragment = AddNewPlayersDialog.newInstance(teamName, teamID);
        newFragment.show(fragmentTransaction, "");
    }

    public void setGameSettings(int innings, int gendersorter) {
        if (inningsView == null) {
            return;
        }
        String inningsText = "Innings: " + innings;
        inningsView.setText(inningsText);
        setGenderSettingDisplay(gendersorter);
    }

    private void setGenderSettingDisplay(int i) {
        if (orderView == null) {
            return;
        }
        if (i == 0) {
            orderView.setVisibility(View.INVISIBLE);
            return;
        }
        orderView.setVisibility(View.VISIBLE);
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("Order: ");
        for (int index = 0; index < i; index++) {
            stringBuilder.append("<font color='#6fa2ef'>M</font>");
        }
        stringBuilder.append("<font color='#f99da2'>F</font>");
        String order = stringBuilder.toString();
        orderView.setText(Html.fromHtml(order));
    }

    private void setGameSummaryView(final int awayRuns, final int homeRuns) {
        SharedPreferences savedGamePreferences = requireActivity()
                .getSharedPreferences(mSelectionID + StatsEntry.GAME, Context.MODE_PRIVATE);
        final int inningNumber = savedGamePreferences.getInt("keyInningNumber", 2);
        int inningDisplay = inningNumber / 2;
        boolean isHome = savedGamePreferences.getBoolean("isHome", false);
        final int totalInnings = savedGamePreferences.getInt(GameActivity.KEY_TOTALINNINGS, 7);
        String awayTeamName = "Away";
        String homeTeamName = "Home";
        if (isHome) {
            homeTeamName = mTeamName;
        } else {
            awayTeamName = mTeamName;
        }
        String summary = awayTeamName + ": " + awayRuns + "    " + homeTeamName + ": " + homeRuns + "\nInning: " + inningDisplay;
        gameSummaryView.setText(summary);
        final String finalAwayTeamName = awayTeamName;
        final String finalHomeTeamName = homeTeamName;
        gameSummaryView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                gameSummaryView.setEnabled(false);
                Intent intent = new Intent(getActivity(), BoxScoreActivity.class);
                Bundle b = new Bundle();
                b.putString("awayTeamName", finalAwayTeamName);
                b.putString("homeTeamName", finalHomeTeamName);
                b.putString("awayTeamID", mSelectionID);
                b.putString("homeTeamID", null);
                b.putInt("totalInnings", totalInnings);
                b.putInt("inningNumber", inningNumber);
                b.putInt("awayTeamRuns", awayRuns);
                b.putInt("homeTeamRuns", homeRuns);
                intent.putExtras(b);
                startActivity(intent);
                gameSummaryView.setEnabled(true);

            }
        });
    }


    private void startGame(boolean isHome) {
        if (sortLineup) {
            openLineupSortDialog(1);
        } else {
//            lineupSubmitButton.setEnabled(true);
            Intent intent = new Intent(getActivity(), TeamGameActivity.class);
            intent.putExtra("isHome", isHome);
            intent.putExtra(GameActivity.KEY_GENDERSORT, 0);
            if (mListener != null) {
                mListener.startGameActivity(intent);
            }
        }
    }

    public void setStartButtonClickable() {
        if (lineupSubmitButton == null) {
            lineupSubmitButton = getView().findViewById(R.id.lineup_submit);
        }
        lineupSubmitButton.setEnabled(true);
    }


    public void openLineupSortDialog(int sortArg) {
        updateAndSubmitLineup();
        int genderSorter = getGenderSorter();
        FragmentManager fragmentManager = requireActivity().getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        DialogFragment newFragment = LineupSortDialog.newInstance(mTeamID, sortArg, genderSorter);
        newFragment.setCancelable(false);
        newFragment.show(fragmentTransaction, "");
    }

    private boolean isLineupOK() {
        return updateAndSubmitLineup() > 3;
    }

    private int getGenderSorter() {
        SharedPreferences genderPreferences = requireActivity()
                .getSharedPreferences(mSelectionID + StatsEntry.SETTINGS, Context.MODE_PRIVATE);
        return genderPreferences.getInt(StatsEntry.COLUMN_GENDER, 0);
    }

    public boolean isHome() {
        RadioGroup radioGroup = getView().findViewById(R.id.radiobtns_away_or_home_team);
        int id = radioGroup.getCheckedRadioButtonId();
        switch (id) {
            case R.id.radio_away:
                return false;
            case R.id.radio_home:
                return true;
        }
        return false;
    }

    private void clearGameDB() {
        String selection = StatsEntry.COLUMN_LEAGUE_ID + "=?";
        String[] selectionArgs = new String[]{mSelectionID};
        requireActivity().getContentResolver().delete(StatsEntry.CONTENT_URI_GAMELOG, selection, selectionArgs);
        requireActivity().getContentResolver().delete(StatsEntry.CONTENT_URI_TEMP, selection, selectionArgs);
        SharedPreferences savedGamePreferences = requireActivity().getSharedPreferences(mSelectionID + StatsEntry.GAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = savedGamePreferences.edit();
        editor.clear();
        editor.apply();
    }

    private List<Player> getPreviousLineup(String teamID) {

        String selection = StatsEntry.COLUMN_TEAM_FIRESTORE_ID + "=? AND " + StatsEntry.COLUMN_LEAGUE_ID + "=?";
        String[] selectionArgs = new String[]{teamID, mSelectionID};
        Cursor cursor = requireActivity().getContentResolver().query(StatsEntry.CONTENT_URI_TEMP,
                null, selection, selectionArgs, null);

        List<Player> previousLineup = new ArrayList<>();

        while (cursor.moveToNext()) {
            previousLineup.add(new Player(cursor, true));
        }
        cursor.close();
        return previousLineup;
    }

    private void setNewLineupToTempDB(List<Player> previousLineup) {

        List<Player> lineup = getLineup();

        String selection = StatsEntry.COLUMN_TEAM_FIRESTORE_ID + "=? AND " + StatsEntry.COLUMN_LEAGUE_ID + "=?";
        String[] selectionArgs = new String[]{mTeamID, mSelectionID};

        ContentResolver contentResolver = requireActivity().getContentResolver();
        contentResolver.delete(StatsEntry.CONTENT_URI_TEMP, selection, selectionArgs);

        for (int i = 0; i < lineup.size(); i++) {
            Player player = lineup.get(i);
            long playerId = player.getPlayerId();
            String playerName = player.getName();
            int gender = player.getGender();
            String firestoreID = player.getFirestoreID();

            ContentValues values = new ContentValues();
            values.put(StatsEntry.COLUMN_LEAGUE_ID, mSelectionID);
            values.put(StatsEntry.COLUMN_FIRESTORE_ID, firestoreID);
            values.put(StatsEntry.COLUMN_PLAYERID, playerId);
            values.put(StatsEntry.COLUMN_NAME, playerName);
            values.put(StatsEntry.COLUMN_TEAM, mTeamName);
            values.put(StatsEntry.COLUMN_TEAM_FIRESTORE_ID, mTeamID);
            values.put(StatsEntry.COLUMN_ORDER, i + 1);
            values.put(StatsEntry.COLUMN_GENDER, gender);

            Player existingPlayer = checkIfPlayerExists(playerId, previousLineup);
            if (existingPlayer != null) {
                values.put(StatsEntry.COLUMN_HR, existingPlayer.getHrs());
                values.put(StatsEntry.COLUMN_3B, existingPlayer.getTriples());
                values.put(StatsEntry.COLUMN_2B, existingPlayer.getDoubles());
                values.put(StatsEntry.COLUMN_1B, existingPlayer.getSingles());
                values.put(StatsEntry.COLUMN_BB, existingPlayer.getWalks());
                values.put(StatsEntry.COLUMN_OUT, existingPlayer.getOuts());
                values.put(StatsEntry.COLUMN_SF, existingPlayer.getSacFlies());
                values.put(StatsEntry.COLUMN_SB, existingPlayer.getStolenBases());
                values.put(StatsEntry.COLUMN_RUN, existingPlayer.getRuns());
                values.put(StatsEntry.COLUMN_RBI, existingPlayer.getRbis());
                previousLineup.remove(existingPlayer);
            }

            contentResolver.insert(StatsEntry.CONTENT_URI_TEMP, values);
        }

        if (!previousLineup.isEmpty()) {
            for (int i = 0; i < previousLineup.size(); i++) {
                Player existingPlayer = previousLineup.get(i);
                ContentValues values = new ContentValues();

                values.put(StatsEntry.COLUMN_LEAGUE_ID, mSelectionID);
                values.put(StatsEntry.COLUMN_FIRESTORE_ID, existingPlayer.getFirestoreID());
                values.put(StatsEntry.COLUMN_PLAYERID, existingPlayer.getPlayerId());
                values.put(StatsEntry.COLUMN_NAME, existingPlayer.getName());
                values.put(StatsEntry.COLUMN_TEAM, mTeamName);
                values.put(StatsEntry.COLUMN_TEAM_FIRESTORE_ID, existingPlayer.getTeamfirestoreid());
                values.put(StatsEntry.COLUMN_ORDER, 999);
                values.put(StatsEntry.COLUMN_GENDER, existingPlayer.getGender());
                values.put(StatsEntry.COLUMN_HR, existingPlayer.getHrs());
                values.put(StatsEntry.COLUMN_3B, existingPlayer.getTriples());
                values.put(StatsEntry.COLUMN_2B, existingPlayer.getDoubles());
                values.put(StatsEntry.COLUMN_1B, existingPlayer.getSingles());
                values.put(StatsEntry.COLUMN_BB, existingPlayer.getWalks());
                values.put(StatsEntry.COLUMN_OUT, existingPlayer.getOuts());
                values.put(StatsEntry.COLUMN_SF, existingPlayer.getSacFlies());
                values.put(StatsEntry.COLUMN_SB, existingPlayer.getStolenBases());
                values.put(StatsEntry.COLUMN_RUN, existingPlayer.getRuns());
                values.put(StatsEntry.COLUMN_RBI, existingPlayer.getRbis());

                contentResolver.insert(StatsEntry.CONTENT_URI_TEMP, values);
            }
            previousLineup.clear();
        }
    }

    private Player checkIfPlayerExists(long playerID, List<Player> players) {
        for (Player player : players) {
            if (playerID == player.getPlayerId()) {
                return player;
            }
        }
        return null;
    }


    private boolean addTeamToTempDB(int requiredFemale) {
        SharedPreferences settingsPreferences = requireActivity()
                .getSharedPreferences(mSelectionID + StatsEntry.SETTINGS, Context.MODE_PRIVATE);
        boolean extraGirlsSorted = settingsPreferences.getBoolean(StatsEntry.SORT_GIRLS, false);
        if(extraGirlsSorted) {
            return sortExtraGirlsToo(requiredFemale);
        } else {
            return sortDefault(requiredFemale);
        }
    }

    private boolean sortDefault(int requiredFemale){
        List<Player> lineup = getLineup();
        ContentResolver contentResolver = requireActivity().getContentResolver();
        int females = 0;
        int males = 0;
        int malesInRow = 0;
        int firstMalesInRow = 0;
        boolean beforeFirstFemale = true;
        boolean notProperOrder = false;
        sortLineup = false;

        for (int i = 0; i < lineup.size(); i++) {
            Player player = lineup.get(i);
            long playerId = player.getPlayerId();
            String playerName = player.getName();
            int gender = player.getGender();
            String firestoreID = player.getFirestoreID();

            ContentValues values = new ContentValues();
            values.put(StatsEntry.COLUMN_LEAGUE_ID, mSelectionID);
            values.put(StatsEntry.COLUMN_FIRESTORE_ID, firestoreID);
            values.put(StatsEntry.COLUMN_PLAYERID, playerId);
            values.put(StatsEntry.COLUMN_NAME, playerName);
            values.put(StatsEntry.COLUMN_TEAM, mTeamName);
            values.put(StatsEntry.COLUMN_TEAM_FIRESTORE_ID, mTeamID);
            values.put(StatsEntry.COLUMN_ORDER, i + 1);
            values.put(StatsEntry.COLUMN_GENDER, gender);
            contentResolver.insert(StatsEntry.CONTENT_URI_TEMP, values);

            if (gender == 0) {
                males++;
                malesInRow++;
                if (beforeFirstFemale) {
                    firstMalesInRow++;
                }
                if (malesInRow > requiredFemale) {
                    notProperOrder = true;
                }
            } else {
                females++;
                malesInRow = 0;
                beforeFirstFemale = false;
            }
        }

        if (requiredFemale < 1) {
            return true;
        }

        int lastMalesInRow = malesInRow;
        if (firstMalesInRow + lastMalesInRow > requiredFemale) {
            notProperOrder = true;
        }
        if (notProperOrder) {
            if (females * requiredFemale >= males) {
                Toast.makeText(getActivity(),
                        "Please set " + mTeamName + "'s lineup properly or edit gender order settings",
                        Toast.LENGTH_LONG).show();
                return false;
            }
            sortLineup = true;
        }
        return true;
    }

    private boolean sortExtraGirlsToo(int requiredFemale) {

        List<Player> lineup = getLineup();
        ContentResolver contentResolver = requireActivity().getContentResolver();
        int females = 0;
        int males = 0;
        int malesInRow = 0;
        int femalesInRow = 0;
        int firstMalesInRow = 0;
        int firstFemalesInRow = 0;
        boolean beforeFirstMale = true;
        boolean beforeFirstFemale = true;
        boolean firstInRowIsMale = true;
        boolean notProperOrder = false;
        sortLineup = false;


        for (int i = 0; i < lineup.size(); i++) {
            Player player = lineup.get(i);
            if (player.getGender() == 0) {
                males++;
            } else {
                females++;
            }
        }
        if (females * requiredFemale > males) {
            Log.d("hooba", "females>males");
        }
        females = 0;
        males = 0;


        for (int i = 0; i < lineup.size(); i++) {
            Player player = lineup.get(i);
            long playerId = player.getPlayerId();
            String playerName = player.getName();
            int gender = player.getGender();
            String firestoreID = player.getFirestoreID();

            ContentValues values = new ContentValues();
            values.put(StatsEntry.COLUMN_LEAGUE_ID, mSelectionID);
            values.put(StatsEntry.COLUMN_FIRESTORE_ID, firestoreID);
            values.put(StatsEntry.COLUMN_PLAYERID, playerId);
            values.put(StatsEntry.COLUMN_NAME, playerName);
            values.put(StatsEntry.COLUMN_TEAM, mTeamName);
            values.put(StatsEntry.COLUMN_TEAM_FIRESTORE_ID, mTeamID);
            values.put(StatsEntry.COLUMN_ORDER, i + 1);
            values.put(StatsEntry.COLUMN_GENDER, gender);
            contentResolver.insert(StatsEntry.CONTENT_URI_TEMP, values);

            if (gender == 0) {
                males++;
                malesInRow++;
                femalesInRow = 0;
                beforeFirstMale = false;
                if (beforeFirstFemale) {
                    firstInRowIsMale = true;
                    firstMalesInRow++;
                }
                if (malesInRow > requiredFemale) {
                    notProperOrder = true;
                }
            } else {
                females++;
                femalesInRow++;
                malesInRow = 0;
                beforeFirstFemale = false;
                if (beforeFirstMale) {
                    firstInRowIsMale = false;
                    firstFemalesInRow++;
                }
                if (femalesInRow > 1) {
                    notProperOrder = true;
                }
            }
        }

        if (requiredFemale < 1) {
            return true;
        }

        if (firstInRowIsMale) {
            if (firstMalesInRow + malesInRow > requiredFemale) {
                notProperOrder = true;
            }
        } else {
            if (firstFemalesInRow + femalesInRow > 1) {
                notProperOrder = true;
            }
        }

        if (notProperOrder) {
            Log.d("POOOO", "notProperOrder");
            if (females * requiredFemale == males) {
                Toast.makeText(getActivity(),
                        "Please set " + mTeamName + "'s lineup properly or edit gender order settings",
                        Toast.LENGTH_LONG).show();
                return false;
            }
            sortLineup = true;
        }
        return true;
    }

    private ArrayList<Player> getLineup() {
        ArrayList<Player> lineup = new ArrayList<>();
        try {
            String selection = StatsEntry.COLUMN_TEAM_FIRESTORE_ID + "=? AND " + StatsEntry.COLUMN_LEAGUE_ID + "=?";
            String[] selectionArgs = new String[]{mTeamID, mSelectionID};
            String sortOrder = StatsEntry.COLUMN_ORDER + " ASC";

            Cursor cursor = requireActivity().getContentResolver().query(StatsEntry.CONTENT_URI_PLAYERS, null,
                    selection, selectionArgs, sortOrder);
            while (cursor.moveToNext()) {
                int order = StatsContract.getColumnInt(cursor, StatsEntry.COLUMN_ORDER);
                if (order < 50) {
                    lineup.add(new Player(cursor, false));
                }
            }
            cursor.close();
            return lineup;
        } catch (Exception e) {
            Toast.makeText(getActivity(), "Lineup Error  " + e, Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    public void changeColorsRV(boolean genderSettingsOn) {
        boolean update = true;
        if (mBoardView.getAdapter(LINEUP_INDEX) != null && mBoardView.getAdapter(BENCH_INDEX) != null) {
            update = ((MyLineupAdapter) mBoardView.getAdapter(LINEUP_INDEX)).changeColors(genderSettingsOn);
            ((MyLineupAdapter) mBoardView.getAdapter(BENCH_INDEX)).changeColors(genderSettingsOn);
        }
        if (update) {
            mBoardView.getAdapter(LINEUP_INDEX).notifyDataSetChanged();
            mBoardView.getAdapter(BENCH_INDEX).notifyDataSetChanged();
        }
    }

    private int updateAndSubmitLineup() {
        String selection = StatsEntry.COLUMN_FIRESTORE_ID + "=? AND " + StatsEntry.COLUMN_LEAGUE_ID + "=?";
        String[] selectionArgs;

        int i = 1;
        List lineupList = mBoardView.getAdapter(LINEUP_INDEX).getItemList();
        for (Object playerObject : lineupList) {
            Pair<Long, Player> pair = (Pair<Long, Player>) playerObject;
            Player player = pair.second;
            String playerfireID = player.getFirestoreID();
            selectionArgs = new String[]{playerfireID, mSelectionID};
            ContentValues values = new ContentValues();
            values.put(StatsEntry.COLUMN_ORDER, i);
            values.put(StatsEntry.COLUMN_LEAGUE_ID, mSelectionID);
            requireActivity().getContentResolver().update(StatsEntry.CONTENT_URI_PLAYERS, values,
                    selection, selectionArgs);
            i++;
        }

        i = 99;
        List benchList = mBoardView.getAdapter(BENCH_INDEX).getItemList();
        for (Object playerObject : benchList) {
            Pair<Long, Player> pair = (Pair<Long, Player>) playerObject;
            Player player = pair.second;
            String playerfireID = player.getFirestoreID();
            selectionArgs = new String[]{playerfireID, mSelectionID};
            ContentValues values = new ContentValues();
            values.put(StatsContract.StatsEntry.COLUMN_ORDER, i);
            values.put(StatsEntry.COLUMN_LEAGUE_ID, mSelectionID);
            requireActivity().getContentResolver().update(StatsContract.StatsEntry.CONTENT_URI_PLAYERS, values,
                    selection, selectionArgs);
        }

        return lineupList.size();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_league, menu);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        Activity activity = getActivity();
        if (!(activity instanceof TeamManagerActivity)) {
            menu.findItem(R.id.action_export_stats).setVisible(false);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                if (getActivity() instanceof TeamManagerActivity) {
                    Intent intent = new Intent(getActivity(), MainActivity.class);
                    startActivity(intent);
                    getActivity().finish();
                } else {
                    super.onOptionsItemSelected(item);
                }
                return true;
            case R.id.change_user_settings:
                Intent settingsIntent = new Intent(getActivity(), UsersActivity.class);
                startActivity(settingsIntent);
                return true;
            case R.id.change_game_settings:
                openGameSettingsDialog();
                return true;
            case R.id.action_export_stats:
                Activity activity = getActivity();
                if (activity instanceof TeamManagerActivity) {
                    TeamManagerActivity teamManagerActivity = (TeamManagerActivity) activity;
                    teamManagerActivity.startLeagueExport(mTeamName);
                    return true;
                }
                return false;
        }
        return false;
    }

    private void openGameSettingsDialog() {
        SharedPreferences settingsPreferences = requireActivity()
                .getSharedPreferences(mSelectionID + StatsEntry.SETTINGS, Context.MODE_PRIVATE);
        int innings = settingsPreferences.getInt(StatsEntry.INNINGS, 7);
        int genderSorter = settingsPreferences.getInt(StatsEntry.COLUMN_GENDER, 0);
        int mercyRuns = settingsPreferences.getInt(StatsEntry.MERCY, 99);
        boolean gameHelp = settingsPreferences.getBoolean(StatsEntry.HELP, true);
        boolean extraGirlsSorted = settingsPreferences.getBoolean(StatsEntry.SORT_GIRLS, false);
        FragmentManager fragmentManager = requireActivity().getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        DialogFragment newFragment = GameSettingsDialog.newInstance(innings, genderSorter, mercyRuns, mSelectionID, 0, gameHelp, extraGirlsSorted);
        newFragment.show(fragmentTransaction, "");
    }

    private int removePlayerFromTeam(String playerFirestoreID) {
        Player player = new Player(-1, playerFirestoreID);
        if (mLineup.contains(player)) {
            mLineup.remove(player);
            return LINEUP_INDEX;
        } else if (mBench.contains(player)) {
            mBench.remove(player);
            return BENCH_INDEX;
        }
        return 2;
    }

    public void removePlayers(List<String> firestoreIDsToDelete) {
        boolean lineupUpdate = false;
        boolean benchUpdate = false;
        for (String firestoreID : firestoreIDsToDelete) {
            int i = removePlayerFromTeam(firestoreID);
            if (i == LINEUP_INDEX) {
                lineupUpdate = true;
            } else if (i == BENCH_INDEX) {
                benchUpdate = true;
            }
        }
        if (lineupUpdate || benchUpdate) {
            resetBoardView();
        }
    }

    public void updatePlayerName(String name, String playerFirestoreID) {
        for (Player player : mLineup) {
            if (player.getFirestoreID().equals(playerFirestoreID)) {
                player.setName(name);
                resetBoardView();
                return;
            }
        }
        for (Player player : mBench) {
            if (player.getFirestoreID().equals(playerFirestoreID)) {
                player.setName(name);
                resetBoardView();
                return;
            }
        }
    }

    public void updatePlayerGender(int gender, String playerFirestoreID) {
        for (Player player : mLineup) {
            if (player.getFirestoreID().equals(playerFirestoreID)) {
                player.setGender(gender);
                resetBoardView();
                return;
            }
        }
        for (Player player : mBench) {
            if (player.getFirestoreID().equals(playerFirestoreID)) {
                player.setGender(gender);
                resetBoardView();
                return;
            }
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof LineupFragment.OnFragmentInteractionListener) {
            mListener = (LineupFragment.OnFragmentInteractionListener) context;
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    @Override
    public void onDestroyView() {
        mBoardView = null;
        super.onDestroyView();
    }

    public interface OnFragmentInteractionListener {
        void startGameActivity(Intent intent);
    }
}