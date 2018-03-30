package xyz.sleekstats.softball.data;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import xyz.sleekstats.softball.MyApp;
import xyz.sleekstats.softball.activities.UsersActivity;
import xyz.sleekstats.softball.objects.ItemMarkedForDeletion;
import xyz.sleekstats.softball.objects.Player;
import xyz.sleekstats.softball.objects.PlayerLog;
import xyz.sleekstats.softball.objects.Team;
import xyz.sleekstats.softball.objects.TeamLog;

import static xyz.sleekstats.softball.data.FirestoreHelper.*;
import static xyz.sleekstats.softball.data.StatsContract.StatsEntry;

public class FirestoreStatkeeperSync implements Parcelable {

    private int playersofar;
    private int teamssofar;
    private String leagueID;

    private onFirestoreSyncListener mListener;

    private Context mContext;
    private FirebaseFirestore mFirestore;


    public FirestoreStatkeeperSync(Context context, String id) {
        this.mContext = context;
        this.leagueID = id;
        mFirestore = FirebaseFirestore.getInstance();

        if (context instanceof onFirestoreSyncListener) {
            mListener = (onFirestoreSyncListener) context;
        }
    }

    //SYNC CHECK

    public void checkForUpdate() {

        final long localTimeStamp = getLocalTimeStamp();

        mFirestore.collection(LEAGUE_COLLECTION).document(leagueID).get()
                .addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                        if (task.isSuccessful()) {
                            long cloudTimeStamp = getCloudTimeStamp(task.getResult());

                            if (cloudTimeStamp > localTimeStamp) {
                                if(mListener != null) {
                                    mListener.onUpdateCheck(true);
                                }
                            } else {
                                if(mListener != null) {
                                    mListener.onUpdateCheck(false);
                                }
                            }
                        } else {
                            if(mListener != null) {
                                mListener.onUpdateCheck(false);
                            }
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        if(mListener != null) {
                            mListener.onUpdateCheck(false);
                        }
                    }
                });
    }


    //SYNCING UPDATES

    public void syncStats() {
        long localTimeStamp = getLocalTimeStamp();

        if (leagueID == null) {
            try {
                MyApp myApp = (MyApp) mContext.getApplicationContext();
                leagueID = myApp.getCurrentSelection().getId();
            } catch (Exception e) {
                return;
            }
        }
        updatePlayers(localTimeStamp);
        updateTeams(localTimeStamp);
    }

    private void updatePlayers(long localTimeStamp) {
        mFirestore.collection(LEAGUE_COLLECTION).document(leagueID).collection(PLAYERS_COLLECTION)
                .whereGreaterThanOrEqualTo(StatsEntry.UPDATE, localTimeStamp)
                .get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        if (task.isSuccessful()) {

                            QuerySnapshot querySnapshot = task.getResult();
                            final int numberOfPlayers = querySnapshot.size();
                            playersofar = 0;
                            if(mListener != null) {
                                mListener.onSyncStart(numberOfPlayers, false);
                            }
                            for (DocumentSnapshot document : querySnapshot) {
                                final Player player = document.toObject(Player.class);
                                final String playerIdString = document.getId();

                                mFirestore.collection(LEAGUE_COLLECTION).document(leagueID).collection(PLAYERS_COLLECTION)
                                        .document(playerIdString).collection(PLAYER_LOGS)
                                        .get()
                                        .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                                            @Override
                                            public void onComplete(@NonNull Task<QuerySnapshot> task) {
                                                if (task.isSuccessful()) {
                                                    playersofar++;

                                                    QuerySnapshot querySnapshot = task.getResult();
                                                    int games = 0;
                                                    int rbi = 0;
                                                    int runs = 0;
                                                    int singles = 0;
                                                    int doubles = 0;
                                                    int triples = 0;
                                                    int hrs = 0;
                                                    int walks = 0;
                                                    int outs = 0;
                                                    int sfs = 0;

                                                    for (DocumentSnapshot document : querySnapshot) {
                                                        PlayerLog playerLog = document.toObject(PlayerLog.class);
                                                        games++;
                                                        rbi += playerLog.getRbi();
                                                        runs += playerLog.getRuns();
                                                        singles += playerLog.getSingles();
                                                        doubles += playerLog.getDoubles();
                                                        triples += playerLog.getTriples();
                                                        hrs += playerLog.getHrs();
                                                        walks += playerLog.getWalks();
                                                        outs += playerLog.getOuts();
                                                        sfs += playerLog.getSacfly();
                                                    }
                                                    final PlayerLog finalLog = new PlayerLog(0, rbi, runs, singles,
                                                            doubles, triples, hrs, outs, walks, sfs);
                                                    final int finalGames = games;


                                                    final DocumentReference docRef = mFirestore.collection(LEAGUE_COLLECTION)
                                                            .document(leagueID).collection(PLAYERS_COLLECTION).document(playerIdString);

                                                    int totalGames = player.getGames() + finalGames;
                                                    int totalSingles = player.getSingles() + finalLog.getSingles();
                                                    int totalDoubles = player.getDoubles() + finalLog.getDoubles();
                                                    int totalTriples = player.getTriples() + finalLog.getTriples();
                                                    int totalHrs = player.getHrs() + finalLog.getHrs();
                                                    int totalWalks = player.getWalks() + finalLog.getWalks();
                                                    int totalOuts = player.getOuts() + finalLog.getOuts();
                                                    int totalRbis = player.getRbis() + finalLog.getRbi();
                                                    int totalRuns = player.getRuns() + finalLog.getRuns();
                                                    int totalSFs = player.getSacFlies() + finalLog.getSacfly();

                                                    player.setGames(totalGames);
                                                    player.setSingles(totalSingles);
                                                    player.setDoubles(totalDoubles);
                                                    player.setTriples(totalTriples);
                                                    player.setHrs(totalHrs);
                                                    player.setWalks(totalWalks);
                                                    player.setOuts(totalOuts);
                                                    player.setRbis(totalRbis);
                                                    player.setRuns(totalRuns);
                                                    player.setSacFlies(totalSFs);

                                                    if (querySnapshot.size() > 0) {
                                                        WriteBatch batch = mFirestore.batch();
                                                        batch.set(docRef, player, SetOptions.merge());

                                                        for (DocumentSnapshot snapshot : querySnapshot) {
                                                            batch.delete(snapshot.getReference());
                                                        }
                                                        batch.commit().addOnCompleteListener(new OnCompleteListener<Void>() {
                                                            @Override
                                                            public void onComplete(@NonNull Task<Void> task) {
                                                            }
                                                        });
                                                    }

                                                    ContentValues values = new ContentValues();
                                                    values.put(StatsEntry.COLUMN_NAME, player.getName());
                                                    values.put(StatsEntry.COLUMN_TEAM, player.getTeam());
                                                    values.put(StatsEntry.COLUMN_TEAM_FIRESTORE_ID, player.getTeamfirestoreid());
                                                    values.put(StatsEntry.COLUMN_1B, player.getSingles());
                                                    values.put(StatsEntry.COLUMN_2B, player.getDoubles());
                                                    values.put(StatsEntry.COLUMN_3B, player.getTriples());
                                                    values.put(StatsEntry.COLUMN_HR, player.getHrs());
                                                    values.put(StatsEntry.COLUMN_RUN, player.getRuns());
                                                    values.put(StatsEntry.COLUMN_RBI, player.getRbis());
                                                    values.put(StatsEntry.COLUMN_BB, player.getWalks());
                                                    values.put(StatsEntry.COLUMN_OUT, player.getOuts());
                                                    values.put(StatsEntry.COLUMN_SF, player.getSacFlies());
                                                    values.put(StatsEntry.COLUMN_G, player.getGames());
                                                    String selection = StatsEntry.COLUMN_FIRESTORE_ID + "=?";

                                                    int rowsUpdated = mContext.getContentResolver().update(StatsEntry.CONTENT_URI_PLAYERS,
                                                            values, selection, new String[]{playerIdString});

                                                    if (rowsUpdated < 1) {
                                                        values.put(StatsEntry.SYNC, true);
                                                        values.put(StatsEntry.COLUMN_NAME, player.getName());
                                                        values.put(StatsEntry.COLUMN_TEAM, player.getTeam());
                                                        values.put(StatsEntry.COLUMN_TEAM_FIRESTORE_ID, player.getTeamfirestoreid());
                                                        values.put(StatsEntry.COLUMN_GENDER, player.getGender());
                                                        values.put(StatsEntry.COLUMN_FIRESTORE_ID, playerIdString);
                                                        mContext.getContentResolver().insert(StatsEntry.CONTENT_URI_PLAYERS, values);
                                                    }
                                                    if(mListener != null) {
                                                        mListener.onSyncUpdate(false);
                                                    }
                                                } else {
                                                    if(mListener != null) {
                                                        mListener.onSyncError("updating players");
                                                    }
                                                }
                                            }
                                        });
                            }
                        } else {
                            if(mListener != null) {
                                mListener.onSyncError("updating players");
                            }
                        }
                    }
                });
    }

    private void updateTeams(long localTimeStamp) {
        mFirestore.collection(LEAGUE_COLLECTION).document(leagueID).collection(TEAMS_COLLECTION)
                .whereGreaterThanOrEqualTo(StatsContract.StatsEntry.UPDATE, localTimeStamp)
                .get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        if (task.isSuccessful()) {
                            QuerySnapshot querySnapshot = task.getResult();
                            final int numberOfTeams = querySnapshot.size();
                            teamssofar = 0;
                            if(mListener != null) {
                                mListener.onSyncStart(numberOfTeams, true);
                            }
                            //loop through teams
                            for (DocumentSnapshot document : querySnapshot) {
                                //Get the document data and ID of a team
                                final Team team = document.toObject(Team.class);
                                final String teamIdString = document.getId();

                                //Get the logs for a team
                                mFirestore.collection(LEAGUE_COLLECTION).document(leagueID).collection(TEAMS_COLLECTION)
                                        .document(teamIdString).collection(TEAM_LOGS)
                                        .get()
                                        .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                                            @Override
                                            public void onComplete(@NonNull Task<QuerySnapshot> task) {
                                                if (task.isSuccessful()) {

                                                    teamssofar++;
                                                    QuerySnapshot querySnapshot = task.getResult();
                                                    int wins = 0;
                                                    int losses = 0;
                                                    int ties = 0;
                                                    int runsScored = 0;
                                                    int runsAllowed = 0;

                                                    //compile logs for team
                                                    for (DocumentSnapshot document : task.getResult()) {
                                                        TeamLog teamLog = document.toObject(TeamLog.class);
                                                        wins += teamLog.getWins();
                                                        losses += teamLog.getLosses();
                                                        ties += teamLog.getTies();
                                                        runsScored += teamLog.getRunsScored();
                                                        runsAllowed += teamLog.getRunsAllowed();
                                                    }
                                                    final TeamLog finalLog = new TeamLog(0, wins, losses, ties, runsScored, runsAllowed);


                                                    DocumentReference docRef = mFirestore.collection(LEAGUE_COLLECTION)
                                                            .document(leagueID).collection(TEAMS_COLLECTION).document(teamIdString);

                                                    int totalWins = team.getWins() + finalLog.getWins();
                                                    int totalLosses = team.getLosses() + finalLog.getLosses();
                                                    int totalTies = team.getTies() + finalLog.getTies();
                                                    int totalRunsScored = team.getTotalRunsScored() + finalLog.getRunsScored();
                                                    int totalRunsAllowed = team.getTotalRunsAllowed() + finalLog.getRunsAllowed();

                                                    team.setWins(totalWins);
                                                    team.setLosses(totalLosses);
                                                    team.setTies(totalTies);
                                                    team.setTotalRunsScored(totalRunsScored);
                                                    team.setTotalRunsAllowed(totalRunsAllowed);

                                                    if (querySnapshot.size() > 0) {
                                                        WriteBatch batch = mFirestore.batch();
                                                        batch.set(docRef, team, SetOptions.merge());

                                                        for (DocumentSnapshot snapshot : querySnapshot) {
                                                            batch.delete(snapshot.getReference());
                                                        }
                                                        batch.commit();
                                                    }

                                                    ContentValues values = new ContentValues();
                                                    values.put(StatsContract.StatsEntry.COLUMN_NAME, team.getName());
                                                    values.put(StatsContract.StatsEntry.COLUMN_WINS, team.getWins());
                                                    values.put(StatsContract.StatsEntry.COLUMN_LOSSES, team.getLosses());
                                                    values.put(StatsContract.StatsEntry.COLUMN_TIES, team.getTies());
                                                    values.put(StatsContract.StatsEntry.COLUMN_RUNSFOR, team.getTotalRunsScored());
                                                    values.put(StatsEntry.COLUMN_RUNSAGAINST, team.getTotalRunsAllowed());
                                                    String selection = StatsEntry.COLUMN_FIRESTORE_ID + "=?";

                                                    int rowsUpdated = mContext.getContentResolver().update(StatsEntry.CONTENT_URI_TEAMS,
                                                            values, selection, new String[]{teamIdString});

                                                    if (rowsUpdated < 1) {
                                                        values.put(StatsEntry.SYNC, true);
                                                        values.put(StatsEntry.COLUMN_NAME, team.getName());
                                                        values.put(StatsEntry.COLUMN_FIRESTORE_ID, teamIdString);
                                                        mContext.getContentResolver().insert(StatsEntry.CONTENT_URI_TEAMS, values);
                                                    }
                                                    if(mListener != null) {
                                                        mListener.onSyncUpdate(true);
                                                    }
                                                } else {
                                                    if(mListener != null) {
                                                        mListener.onSyncError("updating teams");
                                                    }
                                                }
                                            }
                                        });
                            }
                        } else {
                            if(mListener != null) {
                                mListener.onSyncError("updating teams");
                            }
                        }
                    }
                });
    }

    //SYNCING DELETES

    public void deletionCheck(final int level) {
        final long localTimeStamp = getLocalTimeStamp();

        mFirestore.collection(LEAGUE_COLLECTION).document(leagueID).collection(DELETION_COLLECTION)
                .whereGreaterThanOrEqualTo(StatsEntry.TIME, localTimeStamp).get().addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
            @Override
            public void onComplete(@NonNull Task<QuerySnapshot> task) {
                if (task.isSuccessful()) {
                    ArrayList<ItemMarkedForDeletion> itemMarkedForDeletionList = new ArrayList<>();
                    QuerySnapshot querySnapshot = task.getResult();
                    for (DocumentSnapshot documentSnapshot : querySnapshot) {
                        Map<String, Object> data = documentSnapshot.getData();
                        long type = (long) data.get(StatsEntry.TYPE);
                        long gender;
                        String team;
                        if (type == 1) {
                            gender = (long) data.get(StatsEntry.COLUMN_GENDER);
                            team = (String) data.get(StatsEntry.COLUMN_TEAM_FIRESTORE_ID);
                        } else {
                            gender = -1;
                            team = null;
                        }
                        String name = (String) data.get(StatsEntry.COLUMN_NAME);
                        String firestoreID = documentSnapshot.getId();
                        itemMarkedForDeletionList.add(new ItemMarkedForDeletion(firestoreID, type, name, gender, team));
                    }
                    if (itemMarkedForDeletionList.isEmpty()) {
                        updateAfterSync();
                        if(mListener != null) {
                            mListener.proceedToNext();
                        }
                    } else {
                        if (level > UsersActivity.LEVEL_VIEW_WRITE) {
                            Collections.sort(itemMarkedForDeletionList, ItemMarkedForDeletion.nameComparator());
                            Collections.sort(itemMarkedForDeletionList, ItemMarkedForDeletion.typeComparator());
                            if(mListener != null) {
                                mListener.openDeletionCheckDialog(itemMarkedForDeletionList);
                            }
                        } else {
                            deleteItems(itemMarkedForDeletionList);
                            if(mListener != null) {
                                mListener.proceedToNext();
                            }
                        }
                    }
                } else {
                    if(mListener != null) {
                        mListener.onSyncError(DELETION_COLLECTION);
                    }
                }
            }
        });
    }

    public void deleteItems (List<ItemMarkedForDeletion> deleteList) {
        if(deleteList.isEmpty()) {
            return;
        }
        final List<String> currentlyPlaying = checkForCurrentGameInterference();
        boolean keepGame = !currentlyPlaying.isEmpty();
        Uri uri;
        for(ItemMarkedForDeletion item : deleteList) {
            if (item.getType() == 0) {
                uri = StatsEntry.CONTENT_URI_TEAMS;
            } else {
                uri = StatsEntry.CONTENT_URI_PLAYERS;
            }
            String firestoreID = item.getFirestoreID();
            String selection = StatsEntry.COLUMN_FIRESTORE_ID + "=?";
            String[] selectionArgs = new String[]{firestoreID};
            mContext.getContentResolver().delete(uri, selection, selectionArgs);
            if (keepGame && currentlyPlaying.contains(firestoreID)) {
                clearGameDB();
                keepGame = false;
            }
        }
    }

    public void saveItems (List<ItemMarkedForDeletion> saveList) {
        if(saveList.isEmpty()) {
            return;
        }

        for(ItemMarkedForDeletion item : saveList) {
            Map<String, Object> data = new HashMap<>();
            final String firestoreID = item.getFirestoreID();
            String name = item.getName();
            String collection;
            final long type = item.getType();
            if (type == 0) {
                collection = TEAMS_COLLECTION;
            } else {
                collection = PLAYERS_COLLECTION;
                long gender = item.getGender();
                data.put(StatsEntry.COLUMN_GENDER, gender);
                String team = item.getTeam();
                data.put(StatsEntry.COLUMN_TEAM_FIRESTORE_ID, team);
            }
            data.put(StatsEntry.COLUMN_NAME, name);
            DocumentReference itemDoc = mFirestore.collection(LEAGUE_COLLECTION)
                    .document(leagueID).collection(collection).document(firestoreID);
            itemDoc.set(data, SetOptions.merge()).addOnSuccessListener(new OnSuccessListener<Void>() {
                @Override
                public void onSuccess(Void aVoid) {
                    mFirestore.collection(LEAGUE_COLLECTION)
                            .document(leagueID).collection(DELETION_COLLECTION).document(firestoreID).delete();
                    setUpdate(firestoreID, (int) type);
                }
            });
        }
    }

    private void setUpdate(String firestoreID, int type) {
        if (mFirestore == null) {
            mFirestore = FirebaseFirestore.getInstance();
        }

        long timeStamp = System.currentTimeMillis();
        String collection;

        if (type == 0) {
            collection = FirestoreHelper.TEAMS_COLLECTION;
        } else if (type == 1) {
            collection = FirestoreHelper.PLAYERS_COLLECTION;
        } else {
            return;
        }

        DocumentReference documentReference = mFirestore.collection(FirestoreHelper.LEAGUE_COLLECTION).document(leagueID)
                .collection(collection).document(firestoreID);
        documentReference.update(StatsEntry.UPDATE, timeStamp).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Toast.makeText(mContext, "POKEMON! GOTTA CATCH THEM ALLLLL!!!", Toast.LENGTH_LONG).show();
            }
        });
        updateTimeStamps();
    }

    private void clearGameDB() {
        mContext.getContentResolver().delete(StatsEntry.CONTENT_URI_TEMP, null, null);
        mContext.getContentResolver().delete(StatsEntry.CONTENT_URI_GAMELOG, null, null);
        SharedPreferences savedGamePreferences = mContext.getSharedPreferences(leagueID + StatsEntry.GAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = savedGamePreferences.edit();
        editor.clear();
        editor.apply();
    }

    //OTHER
    private List<String> checkForCurrentGameInterference() {
        List<String> currentlyPlaying = new ArrayList<>();
        Cursor cursor = mContext.getContentResolver().query(StatsEntry.CONTENT_URI_TEMP,
                null, null, null, null);
        while (cursor.moveToNext()) {
            String firestoreID = StatsContract.getColumnString(cursor, StatsEntry.COLUMN_FIRESTORE_ID);
            currentlyPlaying.add(firestoreID);
        }
        cursor.close();
        if (!currentlyPlaying.isEmpty()) {
            SharedPreferences gamePreferences
                    = mContext.getSharedPreferences(leagueID + StatsEntry.GAME, Context.MODE_PRIVATE);
            String awayID = gamePreferences.getString("keyAwayTeam", null);
            String homeID = gamePreferences.getString("keyHomeTeam", null);
            currentlyPlaying.add(awayID);
            currentlyPlaying.add(homeID);
        }
        return currentlyPlaying;
    }

    //TIMESTAMP MAINTENANCE

    private long getNewTimeStamp() {
        return System.currentTimeMillis();
    }

    public void updateAfterSync() {
        mFirestore.collection(LEAGUE_COLLECTION).document(leagueID).get()
                .addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                        if (task.isSuccessful()) {
                            long cloudTimeStamp = getCloudTimeStamp(task.getResult());
                            updateLocalTimeStamp(cloudTimeStamp);
                        }
                    }
                });
    }

    private long getLocalTimeStamp() {
//        return 0;
        SharedPreferences updatePreferences = mContext.getSharedPreferences(leagueID + UPDATE_SETTINGS, Context.MODE_PRIVATE);
        return updatePreferences.getLong(LAST_UPDATE, 0);
    }

    private long getCloudTimeStamp(DocumentSnapshot documentSnapshot) {
        Map<String, Object> data = documentSnapshot.getData();
        long cloudTimeStamp;
        Object object = data.get(LAST_UPDATE);
        if (object == null) {
            cloudTimeStamp = 0;
            updateCloudTimeStamp(cloudTimeStamp);
        } else {
            cloudTimeStamp = (long) object;
        }
        return cloudTimeStamp;
    }

    public void updateTimeStamps() {

        final long newTimeStamp = getNewTimeStamp();
        final long localTimeStamp = getLocalTimeStamp();

        mFirestore.collection(LEAGUE_COLLECTION).document(leagueID).get()
                .addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                        if (task.isSuccessful()) {
                            long cloudTimeStamp = getCloudTimeStamp(task.getResult());

                            if (localTimeStamp >= cloudTimeStamp) {
                                updateLocalTimeStamp(newTimeStamp);
                            }
                            updateCloudTimeStamp(newTimeStamp);
                        }
                    }
                });

    }

    private void updateLocalTimeStamp(long timestamp) {
        SharedPreferences updatePreferences = mContext.getSharedPreferences(leagueID + UPDATE_SETTINGS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = updatePreferences.edit();
        editor.putLong(LAST_UPDATE, timestamp);
        editor.apply();
    }

    private void updateCloudTimeStamp(long timestamp) {
        DocumentReference leagueDoc = mFirestore.collection(LEAGUE_COLLECTION).document(leagueID);
        leagueDoc.update(LAST_UPDATE, timestamp);
    }

    //LISTENER
    public interface onFirestoreSyncListener {
        void onUpdateCheck(boolean update);
        void onSyncStart(int numberOf, boolean teams);
        void onSyncUpdate(boolean teams);
        void openDeletionCheckDialog(ArrayList<ItemMarkedForDeletion> deleteList);
        void proceedToNext();
        void onSyncError(String error);
    }

    public void setContext(Context context) {
        mContext = context;
        mListener = (onFirestoreSyncListener) context;
    }

    public void detachListener() {
        mListener = null;

    }

    protected FirestoreStatkeeperSync(Parcel in) {
        playersofar = in.readInt();
        teamssofar = in.readInt();
        leagueID = in.readString();
        mListener = (onFirestoreSyncListener) in.readValue(onFirestoreSyncListener.class.getClassLoader());
        mContext = (Context) in.readValue(Context.class.getClassLoader());
        mFirestore = (FirebaseFirestore) in.readValue(FirebaseFirestore.class.getClassLoader());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(playersofar);
        dest.writeInt(teamssofar);
        dest.writeString(leagueID);
        dest.writeValue(mListener);
        dest.writeValue(mContext);
        dest.writeValue(mFirestore);
    }

    @SuppressWarnings("unused")
    public static final Parcelable.Creator<FirestoreStatkeeperSync> CREATOR = new Parcelable.Creator<FirestoreStatkeeperSync>() {
        @Override
        public FirestoreStatkeeperSync createFromParcel(Parcel in) {
            return new FirestoreStatkeeperSync(in);
        }

        @Override
        public FirestoreStatkeeperSync[] newArray(int size) {
            return new FirestoreStatkeeperSync[size];
        }
    };
}