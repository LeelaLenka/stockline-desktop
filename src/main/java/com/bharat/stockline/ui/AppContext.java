package com.bharat.stockline.ui;

import com.bharat.stockline.dao.ActivityDao;
import com.bharat.stockline.dao.InventoryDao;
import com.bharat.stockline.dao.OrderDao;
import com.bharat.stockline.dao.ProductDao;
import com.bharat.stockline.dao.ReportDao;
import com.bharat.stockline.dao.ReviewDao;
import com.bharat.stockline.dao.StoreDao;
import com.bharat.stockline.dao.UserDao;
import com.bharat.stockline.db.Database;
import com.bharat.stockline.model.User;
import com.bharat.stockline.service.DemoData;
import com.bharat.stockline.service.LiveFeed;
import com.bharat.stockline.service.OrderService;

/** Everything the screens need, wired together once (simple manual dependency injection) */
public final class AppContext {

    public final Database db;
    public final ActivityDao activity;
    public final UserDao users;
    public final StoreDao stores;
    public final ProductDao products;
    public final InventoryDao inventory;
    public final OrderDao orders;
    public final ReviewDao reviews;
    public final ReportDao reports;
    public final OrderService orderService;
    public final DemoData demoData;
    public final LiveFeed liveFeed;
    private User user;

    public AppContext(Database db) {
        this.db = db;
        this.activity = new ActivityDao(db);
        this.users = new UserDao(db);
        this.stores = new StoreDao(db, activity);
        this.products = new ProductDao(db, activity);
        this.inventory = new InventoryDao(db, activity);
        this.orders = new OrderDao(db);
        this.reviews = new ReviewDao(db, activity);
        this.reports = new ReportDao(db);
        this.orderService = new OrderService(db, activity);
        this.demoData = new DemoData(db, activity);
        this.liveFeed = new LiveFeed(activity);
    }

    public User user() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    /** Who did something, for the activity log */
    public String actor() {
        return user == null ? "system" : user.email();
    }
}
