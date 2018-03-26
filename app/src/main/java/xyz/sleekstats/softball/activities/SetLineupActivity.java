package xyz.sleekstats.softball.activities;

/*
 * Copyright (C) 2015 Paul Burke
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.support.v4.app.Fragment;
import android.os.Bundle;


import xyz.sleekstats.softball.MyApp;
import xyz.sleekstats.softball.data.FirestoreHelper;
import xyz.sleekstats.softball.data.StatsContract.StatsEntry;
import xyz.sleekstats.softball.dialogs.AddNewPlayersDialog;
import xyz.sleekstats.softball.dialogs.GameSettingsDialog;
import xyz.sleekstats.softball.fragments.LineupFragment;
import xyz.sleekstats.softball.models.MainPageSelection;
import xyz.sleekstats.softball.models.Player;

import java.util.ArrayList;
import java.util.List;


public class SetLineupActivity extends SingleFragmentActivity
        implements AddNewPlayersDialog.OnListFragmentInteractionListener,
        GameSettingsDialog.OnFragmentInteractionListener {

    private LineupFragment lineupFragment;
    private String mSelectionID;

    @Override
    protected Fragment createFragment() {
        Bundle args = getIntent().getExtras();
        String teamName = null;
        String teamID = null;
        boolean inGame = false;

        if (args != null) {
            teamName = args.getString("team_name");
            teamID = args.getString("team_id");
            inGame = args.getBoolean("ingame");
        } else {
            finish();
        }
        try {
            MyApp myApp = (MyApp) getApplicationContext();
            MainPageSelection mainPageSelection = myApp.getCurrentSelection();
            int type = mainPageSelection.getType();
            mSelectionID = mainPageSelection.getId();
            lineupFragment = LineupFragment.newInstance(mSelectionID, type, teamName, teamID, inGame);
        } catch (Exception e) {
            Intent intent = new Intent(SetLineupActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
        }
        return lineupFragment;
    }

    @Override
    public void onSubmitPlayersListener(List<String> names, List<Integer> genders, String team, String teamID) {
        List<Player> players = new ArrayList<>();

        for (int i = 0; i < names.size() - 1; i++) {
            ContentValues values = new ContentValues();
            String name = names.get(i);
            if (name.isEmpty()) {
                continue;
            }
            int gender = genders.get(i);
            values.put(StatsEntry.COLUMN_NAME, name);
            values.put(StatsEntry.COLUMN_GENDER, gender);
            values.put(StatsEntry.COLUMN_TEAM, team);
            values.put(StatsEntry.COLUMN_TEAM_FIRESTORE_ID, teamID);
            values.put(StatsEntry.COLUMN_ORDER, 99);
            values.put(StatsEntry.ADD, true);
            Uri uri = getContentResolver().insert(StatsEntry.CONTENT_URI_PLAYERS, values);
            if (uri != null) {
                Cursor cursor = getContentResolver().query(uri, null, null, null, null);
                if(cursor.moveToFirst()) {
                    Player player = new Player(cursor, false);
                    players.add(player);
                }
                cursor.close();
            }
        }
        if (!players.isEmpty()) {
            new FirestoreHelper(this, mSelectionID).updateTimeStamps();

            if(lineupFragment != null) {
                lineupFragment.updateBench(players);
            }
        }
    }

    @Override
    public void onGameSettingsChanged(int innings, int genderSorter) {
        boolean genderSettingsOn = genderSorter != 0;

        if (lineupFragment != null) {
            lineupFragment.changeColorsRV(genderSettingsOn);
        }
        setResult(RESULT_OK);
    }
}
