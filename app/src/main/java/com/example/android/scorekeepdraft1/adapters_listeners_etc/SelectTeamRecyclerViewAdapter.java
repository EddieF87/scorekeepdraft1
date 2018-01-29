package com.example.android.scorekeepdraft1.adapters_listeners_etc;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.example.android.scorekeepdraft1.R;
import com.example.android.scorekeepdraft1.dialogs.ChooseOrCreateTeamDialogFragment;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Eddie on 1/29/2018.
 */

public class SelectTeamRecyclerViewAdapter extends RecyclerView.Adapter<SelectTeamRecyclerViewAdapter.TeamViewHolder> {

    private List<String> mTeams;
    private ChooseOrCreateTeamDialogFragment mFragment;
    private SelectTeamRecyclerViewAdapter.OnAdapterInteractionListener mListener;

    public SelectTeamRecyclerViewAdapter(List<String> teams, ChooseOrCreateTeamDialogFragment fragment) {
        mTeams = teams;
        mFragment = fragment;
        mListener = (SelectTeamRecyclerViewAdapter.OnAdapterInteractionListener) fragment;
    }

    @Override
    public TeamViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_team, parent, false);
        return new TeamViewHolder(view);
    }

    @Override
    public void onBindViewHolder(TeamViewHolder holder, int position) {
        String team = mTeams.get(position);
        holder.bindTeam(team);
    }

    @Override
    public int getItemCount() {
        return mTeams.size();
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getItemViewType(int position) {
        return position;
    }

    class TeamViewHolder extends RecyclerView.ViewHolder {
        private TextView mTeamText;
        private String mTeam;

        TeamViewHolder(View view) {
            super(view);
            mTeamText = view.findViewById(R.id.team_text);

        }

        private void bindTeam(String team) {
            mTeam = team;
            mTeamText.setText(mTeam);
            mTeamText.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    mListener.onTeamClicked(mTeam);
                }
            });
        }
    }

    public interface OnAdapterInteractionListener {
        void onTeamClicked(String team);
    }
}
