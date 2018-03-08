package com.example.android.scorekeepdraft1.adapters_listeners_etc;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import static com.example.android.scorekeepdraft1.data.FirestoreHelper.LEAGUE_COLLECTION;

/**
 * Created by Eddie on 3/9/2018.
 */

public class FireTaskLoader extends android.support.v4.content.AsyncTaskLoader<QuerySnapshot> {

    private String userID;
    private boolean loading;

    public FireTaskLoader(@NonNull Context context, String id) {
        super(context);
        userID = id;
    }

    @Override
    protected void onStartLoading() {
        super.onStartLoading();
        forceLoad();
        loading = true;
    }

    @Nullable
    @Override
    public QuerySnapshot loadInBackground() {

        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        Task<QuerySnapshot> task = firestore.collection(LEAGUE_COLLECTION)
                .whereLessThan(userID, 99)
                .get();


        while (!task.isComplete()) {
            if(isLoadInBackgroundCanceled()) {
                return null;
            }
        }

        if(task.isSuccessful()) {
            return task.getResult();
        } else {
            return null;
        }
    }

    @Override
    public boolean isLoadInBackgroundCanceled() {
        return !loading;
    }

    @Override
    public void cancelLoadInBackground() {
        super.cancelLoadInBackground();
        loading = false;
    }
}
