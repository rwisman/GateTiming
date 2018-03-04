package edu.ius.rwisman.gatetiming;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Displays gate detection results in table.
 */

public class ResultsListViewAdapter extends BaseAdapter {
    private final Context context;
    private static ArrayList<GateLevel> list = new ArrayList<GateLevel>();;
    LayoutInflater inflater = null;
    private final double maxMagnitude;
    private int gates;
    ListView listView;

    public ResultsListViewAdapter(final Context context, final double maxMagnitude, final int gates, final ListView listView) {
        super();
        this.context = context;
        this.maxMagnitude = maxMagnitude;
        this.gates = gates;

        this.inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        this.listView = listView;

        View rowView = inflater.inflate(R.layout.detectionresultslist, null);

        TextView tv = (TextView) rowView.findViewById(R.id.gate);
        if (tv.getMeasuredHeight() > 0) {
            ViewGroup.LayoutParams params = listView.getLayoutParams();
            params.height = gates < 5 ? gates * tv.getMeasuredHeight() : 5 * tv.getMeasuredHeight();
            listView.setLayoutParams(params);
            listView.requestLayout();
        }
//
//        if (DEBUG.ON)
//            System.out.println("ResultsListViewAdapter tv.getMeasuredHeight():" + tv.getMeasuredHeight());
    }

    public void update(final GateLevel gateLevel) {
//        if (DEBUG.ON)
//            System.out.println("ResultsListViewAdapter update() gate:" + gateLevel.getGate()+" list.size():"+list.size()+" listView.getFirstVisiblePosition():"+listView.getFirstVisiblePosition());
        GateLevel gl = list.get(gateLevel.getGate()-1);
        gl.level = gateLevel.getLevel();

        listView.post(new Runnable() {
            public void run() {
                listView.setSelection(gateLevel.getGate()-1);
            }
        });

        notifyDataSetChanged();
    }

    public void add(GateLevel gateLevel) {
//        if (DEBUG.ON)
//            System.out.println("ResultsListViewAdapter add() gate:" + gateLevel.getGate()+" list.size():"+list.size());
        list.add(gateLevel);
        gates++;
        notifyDataSetChanged();
    }

    public void clear() {
//        if (DEBUG.ON)
//            System.out.println("ResultsListViewAdapter clear() list.size():"+list.size()+" gates:"+gates);
        list.clear();

        for(int i=1; i <= gates; i++)
            list.add(new GateLevel(i, -1));

        notifyDataSetChanged();
    }

    public ArrayList<GateLevel> getList() {
        return list;
    }

    @Override
    public int getItemViewType(int position) {
        return position;
    }

    @Override
    public int getCount() {
        return list.size();
    }

    @Override
    public Object getItem(int position) {
        return list.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View rowView, ViewGroup parent) {
//        if (DEBUG.ON)
//            System.out.println("ResultsListViewAdapter getView() position:" + position + " gate:" + list.get(position).getGate() + " level:" + list.get(position).getLevel());

        ResultsViewHolder holder=null;
        if(rowView == null) {                                                                      // Create new when == null, Recycle when != null
            rowView = inflater.inflate(R.layout.detectionresultslist, null);
            holder = new ResultsViewHolder();
            holder.gate = (TextView) rowView.findViewById(R.id.gate);
            holder.level = (TextView) rowView.findViewById(R.id.level);
            holder.color = (TextView) rowView.findViewById(R.id.color);
            holder.rating = (TextView) rowView.findViewById(R.id.rating);
            rowView.setTag(holder);
        }
        else
            holder = (ResultsViewHolder)rowView.getTag();

        if (position == 0 && holder.gate.getMeasuredHeight() > 0) {                                 // Size listview for number of gates or 5 maximum
            ViewGroup.LayoutParams params = listView.getLayoutParams();
            params.height = gates < 5 ? gates * (holder.gate.getMeasuredHeight()+listView.getDividerHeight()) : 5 * (holder.gate.getMeasuredHeight()+listView.getDividerHeight());
            listView.setLayoutParams(params);
            listView.requestLayout();
        }

/*
        if (DEBUG.ON)
            System.out.println("ResultsListViewAdapter position:"+position+" holder.gate.getMeasuredHeight():" + (holder.gate.getMeasuredHeight()+listView.getDividerHeight()));

*/
        holder.gate.setText(String.valueOf(list.get(position).getGate()));

        if(list.get(position).getLevel() < 0) {                                                     // Initialized to negative value as flag not to change
            holder.rating.setText(R.string.none);
            holder.level.setText("0%");
            holder.color.setBackgroundColor(Color.BLACK);
            return rowView;
        }

        int ratingLevel = 100 - (int) (list.get(position).getLevel() / maxMagnitude * 100);
        String ratingLevelString=null;
        int background = Color.BLACK;

        if(ratingLevel >= 75) {
            ratingLevelString = context.getString(R.string.good);
            background = Color.GREEN;
        }
        if(ratingLevel >= 50 && ratingLevel < 75) {
            ratingLevelString = context.getString(R.string.poor);
            background = Color.YELLOW;
        }
        if(ratingLevel < 50) {
            ratingLevelString = context.getString(R.string.fail);
            background = Color.RED;
        }

        holder.color.setBackgroundColor(background);
        holder.level.setText(String.valueOf(ratingLevel)+"%");
        holder.rating.setText(ratingLevelString);

        return rowView;
    }
}

class ResultsViewHolder {
    public TextView gate;
    public TextView level;
    public TextView color;
    public TextView rating;
}

