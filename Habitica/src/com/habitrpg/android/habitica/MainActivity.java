package com.habitrpg.android.habitica;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;

import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.core.CrashlyticsCore;
import com.habitrpg.android.habitica.callbacks.HabitRPGUserCallback;
import com.habitrpg.android.habitica.callbacks.TaskCreationCallback;
import com.habitrpg.android.habitica.callbacks.TaskScoringCallback;
import com.habitrpg.android.habitica.callbacks.TaskUpdateCallback;
import com.habitrpg.android.habitica.events.AddTaskTappedEvent;
import com.habitrpg.android.habitica.events.BuyRewardTappedEvent;
import com.habitrpg.android.habitica.events.HabitScoreEvent;
import com.habitrpg.android.habitica.events.TaskCheckedEvent;
import com.habitrpg.android.habitica.events.TaskLongPressedEvent;
import com.habitrpg.android.habitica.events.TaskSaveEvent;
import com.habitrpg.android.habitica.events.TaskTappedEvent;
import com.habitrpg.android.habitica.events.ToggledInnStateEvent;
import com.habitrpg.android.habitica.events.commands.CreateTagCommand;
import com.habitrpg.android.habitica.events.commands.FilterTasksByTagsCommand;
import com.habitrpg.android.habitica.prefs.PrefsActivity;
import com.habitrpg.android.habitica.ui.AvatarWithBarsViewModel;
import com.habitrpg.android.habitica.ui.EditTextDrawer;
import com.habitrpg.android.habitica.ui.MainDrawerBuilder;
import com.habitrpg.android.habitica.ui.adapter.HabitItemRecyclerViewAdapter;
import com.habitrpg.android.habitica.ui.fragments.TaskRecyclerViewFragment;
import com.habitrpg.android.habitica.ui.helpers.Debounce;
import com.instabug.wrapper.support.activity.InstabugAppCompatActivity;
import com.magicmicky.habitrpgwrapper.lib.models.HabitRPGUser;
import com.magicmicky.habitrpgwrapper.lib.models.Tag;
import com.magicmicky.habitrpgwrapper.lib.models.TaskDirection;
import com.magicmicky.habitrpgwrapper.lib.models.TaskDirectionData;
import com.magicmicky.habitrpgwrapper.lib.models.tasks.ItemData;
import com.magicmicky.habitrpgwrapper.lib.models.tasks.Task;
import com.mikepenz.materialdrawer.Drawer;
import com.mikepenz.materialdrawer.DrawerBuilder;
import com.mikepenz.materialdrawer.model.SectionDrawerItem;
import com.mikepenz.materialdrawer.model.SwitchDrawerItem;
import com.mikepenz.materialdrawer.model.interfaces.IDrawerItem;
import com.mikepenz.materialdrawer.model.interfaces.OnCheckedChangeListener;
import com.raizlabs.android.dbflow.runtime.FlowContentObserver;
import com.raizlabs.android.dbflow.sql.builder.Condition;
import com.raizlabs.android.dbflow.sql.language.Select;
import com.raizlabs.android.dbflow.structure.BaseModel;
import com.raizlabs.android.dbflow.structure.Model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import butterknife.ButterKnife;
import butterknife.InjectView;
import de.greenrobot.event.EventBus;
import io.fabric.sdk.android.Fabric;
import retrofit.Callback;
import retrofit.RetrofitError;
import retrofit.client.Response;

public class MainActivity extends InstabugAppCompatActivity implements HabitRPGUserCallback.OnUserReceived,
        TaskScoringCallback.OnTaskScored, FlowContentObserver.OnSpecificModelStateChangedListener,
        TaskCreationCallback.OnHabitCreated, TaskUpdateCallback.OnHabitUpdated,
        Callback<List<ItemData>>, OnCheckedChangeListener {

    static final int TASK_CREATED_RESULT = 1;
    static final int TASK_UPDATED_RESULT = 2;

    //region View Elements
    @InjectView(R.id.viewpager)
    ViewPager materialViewPager;

    @InjectView(R.id.toolbar)
    Toolbar toolbar;

    @InjectView(R.id.detail_tabs)
    TabLayout detail_tabs;

    @InjectView(R.id.avatar_with_bars)
    View avatar_with_bars;

    Drawer drawer;

    Drawer filterDrawer;
    //endregion

    Map<Integer, TaskRecyclerViewFragment> ViewFragmentsDictionary = new HashMap<>();

    List<Task> TaskList = new ArrayList<>();

    private HostConfig hostConfig;
    APIHelper mAPIHelper;

    // just to test the view
    private HabitRPGUser User;

    AvatarWithBarsViewModel avatarInHeader;

    FlowContentObserver observer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Inject Controls
        ButterKnife.inject(this);

        // Receive Events
        EventBus.getDefault().register(this);

        // Initialize Crashlytics
        Crashlytics crashlytics = new Crashlytics.Builder()
                .core(new CrashlyticsCore.Builder().disabled(BuildConfig.DEBUG).build())
                .build();
        Fabric.with(this, crashlytics);

        this.hostConfig = PrefsActivity.fromContext(this);
        if (hostConfig == null || hostConfig.getApi() == null || hostConfig.getApi().equals("") || hostConfig.getUser() == null || hostConfig.getUser().equals("")) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        if (toolbar != null) {
            setSupportActionBar(toolbar);

            ActionBar actionBar = getSupportActionBar();

            if (actionBar != null) {
                actionBar.setDisplayHomeAsUpEnabled(true);
                actionBar.setDisplayShowHomeEnabled(false);
                actionBar.setDisplayShowTitleEnabled(true);
                actionBar.setDisplayUseLogoEnabled(false);
                actionBar.setHomeButtonEnabled(false);
            }

            toolbar.setPadding(0, getResources().getDimensionPixelSize(R.dimen.tool_bar_top_padding), 0, 0);
        }

        materialViewPager.setBackgroundColor(getResources().getColor(R.color.white));

        View mPagerRootView = materialViewPager.getRootView();

        //View avatarHeaderView = mPagerRootView.findViewById(R.id.avatar_with_bars_layout);

        avatarInHeader = new AvatarWithBarsViewModel(this, avatar_with_bars);

        drawer = MainDrawerBuilder.CreateDefaultBuilderSettings(this, toolbar)
                .withSelectedItem(0)
                .build();

        final android.content.Context context = getApplicationContext();

        filterDrawer = new DrawerBuilder()
                .withActivity(this)
                .withDrawerGravity(Gravity.RIGHT)
                .withCloseOnClick(false)
                .append(drawer);

        materialViewPager.setOffscreenPageLimit(6);

        materialViewPager.setCurrentItem(0);

        User = new Select().from(HabitRPGUser.class).where(Condition.column("id").eq(hostConfig.getUser())).querySingle();
        this.observer = new FlowContentObserver();
        this.observer.registerForContentChanges(this.getApplicationContext(), HabitRPGUser.class);

        this.observer.addSpecificModelChangeListener(this);

        try {
            hasItemData = new Select().from(ItemData.class).querySingle() != null;
        } catch (Exception e) {

        }
        SetUserData();
    }

    private boolean hasItemData = false;

    @Override
    protected void onResume() {
        super.onResume();

        if (mAPIHelper == null) {
            this.mAPIHelper = new APIHelper(this, hostConfig);

            mAPIHelper.retrieveUser(new HabitRPGUserCallback(this));
        }
    }

    @Override
    protected void onDestroy() {
        if (observer != null) {
            this.observer.unregisterForContentChanges(this.getApplicationContext());
        }
        EventBus.getDefault().unregister(this);

        super.onDestroy();
    }

    private void showSnackbar(String content) {
        showSnackbar(content, false);
    }

    private void showSnackbar(String content, boolean negative) {
        Fragment f = ViewFragmentsDictionary.get(materialViewPager.getCurrentItem());

        Snackbar snackbar = Snackbar.make(f.getView().findViewById(R.id.fab), content, Snackbar.LENGTH_LONG);

        if (negative) {
            View snackbarView = snackbar.getView();

            //change Snackbar's background color;
            snackbarView.setBackgroundColor(Color.RED);
        }

        snackbar.show();
    }

    //region Events

    public void onEvent(CreateTagCommand event) {
        Tag t = new Tag();
        t.setName(event.tagName);
        t.save();

        mAPIHelper.apiService.createTag(t, new Callback<List<Tag>>() {
            @Override
            public void success(List<Tag> tags, Response response) {
                FillTagFilterDrawer(tags);
            }

            @Override
            public void failure(RetrofitError error) {
                showSnackbar("Error: " + error.getMessage(), true);
            }
        });
    }

    public void onEvent(TaskTappedEvent event) {
        if (event.Task.type.equals("reward"))
            return;

        Bundle bundle = new Bundle();
        bundle.putString("type", event.Task.getType());
        bundle.putString("taskId", event.Task.getId());

        Intent intent = new Intent(this, TaskFormActivity.class);
        intent.putExtras(bundle);
        startActivityForResult(intent, TASK_UPDATED_RESULT);
    }

    public void onEvent(TaskLongPressedEvent event) {
        showSnackbar("LongPress: " + event.Task.text);
    }

    public void onEvent(TaskCheckedEvent event) {
        showSnackbar("ToDo Checked= " + event.Task.getText(), true);
        mAPIHelper.updateTaskDirection(event.Task.getId(), event.Task.getCompleted() ? TaskDirection.down : TaskDirection.up, new TaskScoringCallback(this, event.Task.getId()));
    }

    public void onEvent(HabitScoreEvent event) {
        mAPIHelper.updateTaskDirection(event.Habit.getId(), event.Up ? TaskDirection.up : TaskDirection.down, new TaskScoringCallback(this, event.Habit.getId()));
    }

    public void onEvent(AddTaskTappedEvent event) {
        Bundle bundle = new Bundle();
        bundle.putString("type", event.ClassType.getSimpleName().toLowerCase());

        Intent intent = new Intent(this, TaskFormActivity.class);
        intent.putExtras(bundle);
        startActivityForResult(intent, TASK_CREATED_RESULT);
    }

    public void onEvent(final BuyRewardTappedEvent event) {
        final String rewardKey = event.Reward.getId();

        if (User.getStats().getGp() < event.Reward.getValue()) {
            showSnackbar("Not enough Gold", true);
            return;
        }

        mAPIHelper.updateTaskDirection(rewardKey, TaskDirection.down, new TaskScoringCallback(this, rewardKey));

        /*
        if (event.Reward instanceof RewardItem) {
            if (rewardKey.equals("potion")) {
                int currentHp = User.getStats().getHp().intValue();
                int maxHp = User.getStats().getMaxHealth();

                if(currentHp == maxHp) {
                    showSnackbar("You don't need to buy an health potion", true);
                    return;
                }
            }

            mAPIHelper.apiService.buyItem(event.Reward.getId(), new Callback<Void>() {
                @Override
                public void success(Void aVoid, Response response) {

                    switch (rewardKey) {
                        case "potion":
                            double newHp = Math.min(User.getStats().getMaxHealth(), User.getStats().getHp() + 15);
                            User.getStats().setHp(newHp);

                            updateHeader();

                            break;
                    }

                    showSnackbar("Buy Reward Successful " + event.Reward.getText());
                }

                @Override
                public void failure(RetrofitError error) {

                    showSnackbar("Buy Reward Error " + event.Reward.getText(), true);
                }
            });
        } else {
            // User created Rewards
        }*/
    }

    public void onEvent(final TaskSaveEvent event) {
        Task task = (Task) event.task;
        if (event.created) {
            this.mAPIHelper.createNewTask(task, new TaskCreationCallback(this));
        } else {
            this.mAPIHelper.updateTask(task, new TaskUpdateCallback(this));
        }
    }

    public void onEvent(ToggledInnStateEvent event) {
        User.getPreferences().setSleep(event.Inn);

        updateUserAvatars();
    }

    //endregion Events

    private void notifyUser(double xp, double hp, double gold,
                            double lvl, double delta) {
        StringBuilder message = new StringBuilder();
        boolean neg = false;
        if (lvl > User.getStats().getLvl()) {
            message.append(getString(R.string.lvlup));
            //If user lvl up, we need to fetch again the data from the server...
            this.mAPIHelper.retrieveUser(new HabitRPGUserCallback(this));
            User.getStats().setLvl((int) lvl);
            showSnackbar(message.toString());
        } else {
            com.magicmicky.habitrpgwrapper.lib.models.Stats stats = User.getStats();

            if (xp > stats.getExp()) {
                message.append(" + ").append(round(xp - stats.getExp(), 2)).append(" XP");
                User.getStats().setExp(xp);
            }
            if (hp != stats.getHp()) {
                neg = true;
                message.append(" - ").append(round(stats.getHp() - hp, 2)).append(" HP");
                User.getStats().setHp(hp);
            }
            if (gold > stats.getGp()) {
                message.append(" + ").append(round(gold - stats.getGp(), 2)).append(" GP");
                stats.setGp(gold);
            } else if (gold < stats.getGp()) {
                neg = true;
                message.append(" - ").append(round(stats.getGp() - gold, 2)).append(" GP");
                stats.setGp(gold);
            }
            showSnackbar(message.toString(), neg);

            updateUserAvatars();
        }
    }

    static public Double round(Double value, int n) {
        return (Math.round(value * Math.pow(10, n))) / (Math.pow(10, n));
    }

    public void loadTaskLists() {
        android.support.v4.app.FragmentManager fragmentManager = getSupportFragmentManager();

        materialViewPager.setAdapter(new FragmentPagerAdapter(fragmentManager) {

            int oldPosition = -1;

            @Override
            public Fragment getItem(int position) {
                int layoutOfType;
                TaskRecyclerViewFragment fragment;

                String fragmentkey = "Recycler$" + position;

                switch (position) {
                    case 0:
                        layoutOfType = R.layout.habit_item_card;
                        fragment = TaskRecyclerViewFragment.newInstance(new HabitItemRecyclerViewAdapter("habit", Task.class, layoutOfType, HabitItemRecyclerViewAdapter.HabitViewHolder.class, MainActivity.this), Task.class);

                        break;
                    case 1:
                        layoutOfType = R.layout.daily_item_card;
                        fragment = TaskRecyclerViewFragment.newInstance(new HabitItemRecyclerViewAdapter("daily", Task.class, layoutOfType, HabitItemRecyclerViewAdapter.DailyViewHolder.class, MainActivity.this), Task.class);
                        break;
                    case 3:
                        layoutOfType = R.layout.reward_item_card;
                        fragment = TaskRecyclerViewFragment.newInstance(new HabitItemRecyclerViewAdapter("reward", Task.class, layoutOfType, HabitItemRecyclerViewAdapter.RewardViewHolder.class, MainActivity.this), Task.class);
                        break;
                    default:
                        layoutOfType = R.layout.todo_item_card;
                        fragment = TaskRecyclerViewFragment.newInstance(new HabitItemRecyclerViewAdapter("todo", Task.class, layoutOfType, HabitItemRecyclerViewAdapter.TodoViewHolder.class, MainActivity.this), Task.class);
                }

                ViewFragmentsDictionary.put(position, fragment);

                return fragment;
            }

            @Override
            public int getCount() {
                return 4;
            }

            @Override
            public CharSequence getPageTitle(int position) {
                switch (position) {
                    case 0:
                        return "Habits";
                    case 1:
                        return "Dailies";
                    case 2:
                        return "Todos";
                    case 3:
                        return "Rewards";
                }
                return "";
            }
        });


        detail_tabs.setupWithViewPager(materialViewPager);

    }


    public int adjustAlpha(int color, float factor) {
        int alpha = Math.round(Color.alpha(color) * factor);
        int red = Color.red(color);
        int green = Color.green(color);
        int blue = Color.blue(color);
        return Color.argb(alpha, red, green, blue);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main_activity, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        switch (id) {
            case R.id.action_search:
                filterDrawer.openDrawer();
                return true;
            case R.id.action_reload:
                mAPIHelper.retrieveUser(new HabitRPGUserCallback(this));
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void updateUserAvatars() {
        avatarInHeader.UpdateData(User);
    }

    private void updateHeader() {
        updateUserAvatars();
        toolbar.setTitle(User.getProfile().getName() + " - Lv" + User.getStats().getLvl());

        android.support.v7.app.ActionBarDrawerToggle actionBarDrawerToggle = drawer.getActionBarDrawerToggle();

        if (actionBarDrawerToggle != null) {
            actionBarDrawerToggle.setDrawerIndicatorEnabled(true);
        }
    }

    @Override
    public void onUserReceived(HabitRPGUser user) {
    }

    @Override
    public void onUserFail() {

    }


    @Override
    public void success(List<ItemData> items, Response response) {

        // TODO Order Rewards
        // TODO add Gear Images

        Condition.In keyCondition = Condition.column("key").in("potion");

        for (ItemData item : items) {
            keyCondition = keyCondition.and(item.key);
        }

/*
        ConditionQueryBuilder<ItemData> queryBuilder = new ConditionQueryBuilder<ItemData>(ItemData.class,
                keyCondition);

        List<ItemData> itemsFromDb = new Select().from(ItemData.class).where(queryBuilder).queryList();

        ArrayList<RewardItem> rewardList = new ArrayList<>();

        for (ItemData item : itemsFromDb) {
            RewardItem reward = new RewardItem();
            reward.text = item.text;
            reward.notes = item.notes;
            reward.value = item.value;
            reward.setId(item.key);

            rewardList.add(reward);
        }

        GearRewards.clear();
        GearRewards.addAll(rewardList);
        */
    }

    @Override
    public void failure(RetrofitError error) {

    }

    @Override
    public void onTaskDataReceived(TaskDirectionData data) {
        notifyUser(data.getExp(), data.getHp(), data.getGp(), data.getLvl(), data.getDelta());
    }

    @Override
    public void onTaskScoringFailed() {

    }

    @Override
    public void onModelStateChanged(Class<? extends Model> aClass, BaseModel.Action action, String s, String s1) {
        User = new Select().from(HabitRPGUser.class).where(Condition.column("id").eq(hostConfig.getUser())).querySingle();

        SetUserData();
    }

    private boolean taskListAlreadyAdded;

    private boolean getContentCalled = false;

    private void SetUserData() {
        if (User != null) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (!taskListAlreadyAdded) {
                        taskListAlreadyAdded = true;
                        loadTaskLists();
                        FillTagFilterDrawer(User.getTags());
                    }
                    updateHeader();
                }
            });
        }
    }

    // TaskCreationCallback
    @Override
    public void onTaskCreated(Task habit) {
        habit.save();
    }

    @Override
    public void onTaskCreationFail() {

    }

    // TaskUpdateCallback
    @Override
    public void onTaskUpdated(Task habit) {
        habit.save();
    }

    @Override
    public void onTaskUpdateFail() {

    }

    // Filter Tags

    public void FillTagFilterDrawer(List<Tag> tagList) {
        filterDrawer.removeAllItems();
        filterDrawer.addItems(
                new SectionDrawerItem().withName("Filter by Tag"),
                new EditTextDrawer()
        );

        for (Tag t : tagList) {
            filterDrawer.addItem(new SwitchDrawerItem()
                            .withName(t.getName())
                            .withTag(t)
                            .withDescription("" + t.getTasks().size())
                            .withOnCheckedChangeListener(this)
            );
        }
    }

    // A Filter was checked

    private Debounce filterChangedHandler = new Debounce(1500, 1000) {
        @Override
        public void execute() {
            ArrayList<String> tagList = new ArrayList<String>();

            for (Map.Entry<String, Boolean> f : tagFilterMap.entrySet()) {
                if (f.getValue()) {
                    tagList.add(f.getKey());
                }
            }

            EventBus.getDefault().post(new FilterTasksByTagsCommand(tagList));
        }
    };


    private HashMap<String, Boolean> tagFilterMap = new HashMap<>();

    @Override
    public void onCheckedChanged(IDrawerItem iDrawerItem, CompoundButton compoundButton, boolean b) {
        Tag t = (Tag) iDrawerItem.getTag();

        if (t != null) {
            tagFilterMap.put(t.getId(), b);
            filterChangedHandler.hit();

            showSnackbar(t.getName() + " : " + b);
        }
    }
}