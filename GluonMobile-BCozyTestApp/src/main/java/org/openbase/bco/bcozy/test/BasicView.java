package org.openbase.bco.bcozy.test;

import com.gluonhq.charm.glisten.control.AppBar;
import com.gluonhq.charm.glisten.control.Icon;
import com.gluonhq.charm.glisten.mvc.View;
import com.gluonhq.charm.glisten.visual.MaterialDesignIcon;
import com.guigarage.responsive.ResponsiveHandler;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.openbase.bco.bcozy.BCozy;
import org.openbase.bco.bcozy.controller.*;
import org.openbase.bco.bcozy.util.ThemeManager;
import org.openbase.bco.bcozy.view.BackgroundPane;
import org.openbase.bco.bcozy.view.ForegroundPane;
import org.openbase.bco.bcozy.view.InfoPane;
import org.openbase.bco.bcozy.view.LoadingPane;
import org.openbase.bco.registry.remote.Registries;
import org.openbase.jps.core.JPService;
import org.openbase.jul.exception.FatalImplementationErrorException;
import org.openbase.jul.exception.InitializationException;
import org.openbase.jul.exception.NotAvailableException;
import org.openbase.jul.exception.printer.ExceptionPrinter;
import org.openbase.jul.exception.printer.LogLevel;
import org.openbase.jul.pattern.Observer;
import org.openbase.jul.pattern.Remote.ConnectionState;
import org.openbase.jul.schedule.GlobalCachedExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class BasicView extends View {

    /**
     * Application name.
     */
    public static final String APP_NAME = BCozy.class.getSimpleName().toLowerCase();

    /**
     * Application logger.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(BCozy.class);

//    public static Stage primaryStage;

    private LoadingPane loadingPane;
    private ContextMenuController contextMenuController;
    private LocationPaneController locationPaneController;
    private ForegroundPane foregroundPane;
    private UnitsPaneController unitsPaneController;
    private MaintenanceLayerController maintenanceLayerController;
    private EditingLayerController editingLayerController;
    private Future initTask;

    private Scene mainScene;

    private static Observer<ConnectionState> connectionObserver;


    public BasicView(String name) {
        super(name);

        Label label = new Label("Hello JavaFX World!");

        Button button = new Button("Change the World!");
        button.setGraphic(new Icon(MaterialDesignIcon.LANGUAGE));
        button.setOnAction(e -> label.setText("Hello JavaFX Universe!"));

        VBox mainScene = new VBox(15.0);
        mainScene.setAlignment(Pos.CENTER);

        connectionObserver = (source, data) -> {
            switch (data) {
                case CONNECTED:
                    // recover default
                    InfoPane.confirmation("connected");
                    break;
                case CONNECTING:
                    // green
                    InfoPane.warn("connecting");
                    break;
                case DISCONNECTED:
                    InfoPane.error("disconnected");
                    // red
                    break;
                case UNKNOWN:
                default:
                    // blue
                    break;
            }
        };

        try {
////            BCozy.primaryStage = primaryStage;
////            registerResponsiveHandler();
////
            final double screenWidth = Screen.getPrimary().getVisualBounds().getWidth();
            final double screenHeight = Screen.getPrimary().getVisualBounds().getHeight();
//            primaryStage.setTitle("BCO BCozy");
//
            final StackPane root = new StackPane();
            foregroundPane = new ForegroundPane(screenHeight, screenWidth);
            foregroundPane.setMinHeight(root.getHeight());
            foregroundPane.setMinWidth(root.getWidth());
            final BackgroundPane backgroundPane = new BackgroundPane(foregroundPane);
//
            loadingPane = new LoadingPane(screenHeight, screenWidth);
            loadingPane.setMinHeight(root.getHeight());
            loadingPane.setMinWidth(root.getWidth());
            root.getChildren().addAll(backgroundPane, foregroundPane, loadingPane);
//
            ThemeManager.getInstance().loadDefaultTheme();
//
            new MainMenuController(foregroundPane);
//            new CenterPaneController(foreCgroundPane);

            contextMenuController = new ContextMenuController(foregroundPane, backgroundPane.getLocationPane());
            locationPaneController = new LocationPaneController(backgroundPane.getLocationPane());
            unitsPaneController = new UnitsPaneController(backgroundPane.getUnitsPane(), backgroundPane.getLocationPane());
            maintenanceLayerController = new MaintenanceLayerController(backgroundPane.getMaintenancePane(), backgroundPane.getLocationPane());
            editingLayerController = new EditingLayerController(backgroundPane.getEditingPane(), backgroundPane.getLocationPane());

//            ResponsiveHandler.addResponsiveToWindow(primaryStage);
//            primaryStage.show();

            InfoPane.confirmation("Welcome");
            try {
                Registries.getUnitRegistry().addConnectionStateObserver(connectionObserver);
            } catch (NotAvailableException ex) {
                ExceptionPrinter.printHistory("Could not register bco connection observer!", ex, LOGGER);
            }

            mainScene.getChildren().addAll(root);
            initRemotesAndLocation();
        } catch (final Exception ex) {
            ExceptionPrinter.printHistory("Could not start " + JPService.getApplicationName(), ex, LOGGER);
        }

        setCenter(mainScene);
    }

    @Override
    protected void updateAppBar(AppBar appBar) {
        appBar.setNavIcon(MaterialDesignIcon.MENU.button(e -> System.out.println("Menu")));
        appBar.setTitleText("Basic View");
        appBar.getActionItems().add(MaterialDesignIcon.SEARCH.button(e -> System.out.println("Search")));
    }

    private void initRemotesAndLocation() {
        initTask = GlobalCachedExecutorService.submit(new Task() {
            @Override
            protected Object call() throws java.lang.Exception {
                try {
                    loadingPane.info("waitForConnection");
                    Registries.waitForData();

                    loadingPane.info("fillContextMenu");
                    foregroundPane.init();

                    contextMenuController.initTitledPaneMap();

                    loadingPane.info("connectLocationRemote");
                    locationPaneController.connectLocationRemote();
                    unitsPaneController.connectUnitRemote();
                    maintenanceLayerController.connectUnitRemote();
                    editingLayerController.connectUnitRemote();
                    loadingPane.info("done");

                    return null;
                } catch (Exception ex) {
                    loadingPane.error("errorDuringStartup");
                    Thread.sleep(3000);
                    Exception exx = new FatalImplementationErrorException("Could not init panes", this, ex);
                    ExceptionPrinter.printHistoryAndExit(exx, LOGGER);
                    throw exx;
                }
            }

            @Override
            protected void succeeded() {
                super.succeeded();
                loadingPane.setVisible(false);
            }
        });
    }

//    @Override
//    public void stop() {
//        boolean errorOccured = false;
//
//        if (initTask != null && !initTask.isDone()) {
//            initTask.cancel(true);
//            try {
//                initTask.get();
//            } catch (InterruptedException | ExecutionException ex) {
//                ExceptionPrinter.printHistory("Initialization phase canceled because of application shutdown.", ex, LOGGER, LogLevel.INFO);
//                errorOccured = true;
//            } catch (CancellationException ex) {
//                ExceptionPrinter.printHistory("Initialization phase failed but application shutdown was initialized anyway.", ex, LOGGER, LogLevel.WARN);
//            }
//        }
//
//        try {
//            Registries.getUnitRegistry().removeConnectionStateObserver(connectionObserver);
//        } catch (NotAvailableException ex) {
//            ExceptionPrinter.printHistory("Could not remove bco connection observer!", ex, LOGGER);
//        } catch (InterruptedException ex) {
//            Thread.currentThread().interrupt();
//        }
//
//        try {
//            super.stop();
//        } catch (Exception ex) { //NOPMD
//            ExceptionPrinter.printHistory("Could not stop " + JPService.getApplicationName() + "!", ex, LOGGER);
//            errorOccured = true;
//        }
//
//        // Call system exit to trigger all shutdown deamons.
//        if (errorOccured) {
//            System.exit(255);
//        }
//        System.exit(0);
//    }


//    private static void registerResponsiveHandler() {
//        LOGGER.debug("Register responsive handler...");
//        ResponsiveHandler.setOnDeviceTypeChanged((over, oldDeviceType, newDeviceType) -> {
//            switch (newDeviceType) {
//                case LARGE:
//                    adjustToLargeDevice();
//                    break;
//                case MEDIUM:
//                    adjustToMediumDevice();
//                    break;
//                case SMALL:
//                    adjustToSmallDevice();
//                    break;
//                case EXTRA_SMALL:
//                    adjustToExtremeSmallDevice();
//                    break;
//                default:
//                    break;
//            }
//        });
//    }

    private static void adjustToLargeDevice() {
        LOGGER.debug("Detected Large Device");
    }

    private static void adjustToMediumDevice() {
        LOGGER.debug("Detected Medium Device");
    }

    private static void adjustToSmallDevice() {
        LOGGER.debug("Detected Small Device");
    }

    private static void adjustToExtremeSmallDevice() {
        LOGGER.debug("Detected Extreme Small Device");
    }
}
