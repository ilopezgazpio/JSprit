package Jsprit_tests;

import com.graphhopper.jsprit.analysis.toolbox.GraphStreamViewer;
import com.graphhopper.jsprit.analysis.toolbox.Plotter;
import com.graphhopper.jsprit.core.algorithm.VehicleRoutingAlgorithm;
import com.graphhopper.jsprit.core.algorithm.box.Jsprit;
import com.graphhopper.jsprit.core.algorithm.state.StateManager;
import com.graphhopper.jsprit.core.problem.AbstractJob;
import com.graphhopper.jsprit.core.problem.Location;
import com.graphhopper.jsprit.core.problem.VehicleRoutingProblem;
import com.graphhopper.jsprit.core.problem.constraint.ConstraintManager;
import com.graphhopper.jsprit.core.problem.constraint.PickupAndDeliverLIFOConstraintHardAct;
import com.graphhopper.jsprit.core.problem.job.Service;
import com.graphhopper.jsprit.core.problem.job.Shipment;
import com.graphhopper.jsprit.core.problem.solution.VehicleRoutingProblemSolution;
import com.graphhopper.jsprit.core.problem.solution.route.activity.TimeWindow;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleImpl;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleType;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleTypeImpl;
import com.graphhopper.jsprit.core.reporting.SolutionPrinter;
import com.graphhopper.jsprit.core.util.Coordinate;
import com.graphhopper.jsprit.core.util.EuclideanDistanceCalculator;
import com.graphhopper.jsprit.core.util.Solutions;
import com.graphhopper.jsprit.core.util.VehicleRoutingTransportCostsMatrix;
import com.graphhopper.jsprit.io.problem.VrpXMLWriter;
import com.lbt05.EvilTransform.GCJPointer;
import com.lbt05.EvilTransform.GeoPointer;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class Pruebas_Jsprit
{
    // Global variables
    final static String datePattern = "dd-MMM-yyyy HH:mm";
    final static int WEIGHT_INDEX = 0;

    /*******************
     Location builder
     ******************/
    public static class MyLocationBuilder
    {
        public static Location build(String name, String latitude, String longitude)
        {
            return Location.Builder.newInstance()
                    .setName(name)
                    .setCoordinate(
                            new Coordinate(
                                    Double.parseDouble(latitude),
                                    Double.parseDouble(longitude)
                            )
                    )
                    .build();
        }
    }

     /***********
     Job builder
     ***********/
    public static class MyJobBuilder
    {
        public static AbstractJob build(String refCount, String refJourney, String refDrop, String refFrom, String refTo, String capacity,
                                        String pickupName, String pickupLatitude, String pickupLongitude, String pickupTimeWindowStart, String pickupTimeWindowEnd,
                                        String deliveryName, String deliveryLatitude, String deliveryLongitude,  String deliveryTimeWindowStart, String deliveryTimeWindowEnd
                                        ) throws ParseException
        {
            return Shipment.Builder.newInstance(
                    "Ref: "+ refCount + "-" + refJourney +":"+ refDrop + ". From: " + refFrom + " To: " + refTo)
                    .addSizeDimension(WEIGHT_INDEX, Integer.parseInt(capacity))
                    .setPickupLocation(
                            Location.Builder.newInstance()
                                    .setName(pickupName)
                                    .setCoordinate(new Coordinate(
                                            Double.parseDouble(pickupLatitude),
                                            Double.parseDouble(pickupLongitude)))
                                    .build())
                    .setDeliveryLocation(
                            Location.Builder.newInstance()
                                    .setName(deliveryName)
                                    .setCoordinate(new Coordinate(
                                            Double.parseDouble(deliveryLatitude),
                                            Double.parseDouble(deliveryLongitude)))
                                    .build())
                    .addPickupTimeWindow(
                            TimeWindow.newInstance(
                                    new SimpleDateFormat(datePattern, Locale.UK).parse(pickupTimeWindowStart).getTime() / 60000 , // milliseconds to minutes
                                    new SimpleDateFormat(datePattern, Locale.UK).parse(pickupTimeWindowEnd).getTime() / 60000
                            )
                    )
                    .addDeliveryTimeWindow(
                            TimeWindow.newInstance(
                                    new SimpleDateFormat(datePattern, Locale.UK).parse(deliveryTimeWindowStart).getTime() / 60000 ,
                                    new SimpleDateFormat(datePattern, Locale.UK).parse(deliveryTimeWindowEnd).getTime() / 60000
                            )
                    )
                    .setPickupServiceTime(60)
                    .setDeliveryServiceTime(60)
                    .build();
        }
    }





    public static void main (String [] args) throws ParseException
    {
        final int NUM_THREADS = 8;
        final int MAX_TRUCKS_DEPOT = 1;

        /**********************
         Create problem builder
         **********************/
        VehicleRoutingProblem.Builder vrpBuilder = VehicleRoutingProblem.Builder.newInstance();


        /*************
         Create fleet
         *************/

        // Truck builder
        VehicleType Truck_type1 = VehicleTypeImpl.Builder.newInstance("Truck_type1")
                .addCapacityDimension(WEIGHT_INDEX, 52)
                .setCostPerDistance(0.00046)
                .setCostPerServiceTime(1)
                .setCostPerTransportTime(1)
                .setCostPerWaitingTime(1)
                .setMaxVelocity(1000) // in (meter / min) units (60 km/h)
                .build();

        // Crear instancias de vehiculos y depots
        List<Location> depots = new ArrayList<Location>();
        depots.add( MyLocationBuilder.build("Depot 1", "0.0", "0.0") );

        List<VehicleImpl> fleet = new ArrayList<VehicleImpl>();
        int truck_count = 1;
        for (Location depot : depots)
        {
            for (int i = 0; i < MAX_TRUCKS_DEPOT; i++)
            {
                VehicleImpl truck = VehicleImpl.Builder.newInstance("Generic Truck " + String.valueOf(truck_count++))
                        .setStartLocation(depot)
                        .setType(Truck_type1)
                        .setCompany("Company")
                        .setReturnToDepot(true)
                        .build();

                fleet.add(truck);
            }
        }

        for (VehicleImpl current : fleet)
        {
            vrpBuilder.addVehicle(current);
        }

        //set fleetsize finite
        vrpBuilder.setFleetSize(VehicleRoutingProblem.FleetSize.FINITE);

        /*****************
         * Add services
         *****************/
        List <AbstractJob> services = new ArrayList<AbstractJob>();

        services.add(
                MyJobBuilder.build(
                        "C1", "J1", "D1", "1", "1","1",
                        "1", "0.0", "0.0","20-JAN-2020 17:30", "20-JAN-2020 18:30",
                        "1", "0.0", "0.0", "20-JAN-2020 23:30", "20-JAN-2020 24:30")
        );

        for (AbstractJob current : services)
        {
            vrpBuilder
                    .addJob(current);
        }


        /*************
         * Create vrp
         *************/
        VehicleRoutingTransportCostsMatrix costMatrix = createMatrix(vrpBuilder);
        vrpBuilder.setRoutingCost(costMatrix);
        VehicleRoutingProblem vrp = vrpBuilder.build();

        /********************
         * Get the algorithm
         *******************/
        StateManager stateManager = new StateManager(vrp);
        ConstraintManager constraintManager = new ConstraintManager(vrp, stateManager);

        //ADD CONSTRAINTS HERE
        /*
        constraintManager
                .addConstraint(
                        new PickupAndDeliverLIFOConstraintHardAct(), ConstraintManager.Priority.CRITICAL
                );
         */

        VehicleRoutingAlgorithm vra = Jsprit.Builder.newInstance(vrp)
                .setProperty(Jsprit.Parameter.THREADS, String.valueOf(8))
                .setProperty(Jsprit.Parameter.THRESHOLD_ALPHA.toString(), String.valueOf(0.01D))
                .setStateAndConstraintManager(stateManager, constraintManager)
                .buildAlgorithm();

        // VehicleRoutingAlgorithm vra = Jsprit.createAlgorithm(vrp);
        vra.setMaxIterations(256);
        vra.getSearchStrategyManager()
                .setRandom(new Random());

        /************************
         Search a solution
         ***********************/
        Collection<VehicleRoutingProblemSolution> solutions = vra.searchSolutions();

        /*************
         * Get the best
         *************/
        VehicleRoutingProblemSolution bestSolution = Solutions.bestOf(solutions);

        /************
         * Print best
         *************/
        System.out.println("Printing best solution VERBOSE MODE");
        System.out.println("===================================");
        SolutionPrinter.print(vrp, bestSolution, SolutionPrinter.Print.VERBOSE);

        System.out.println("Printing best solution CONCISE MODE");
        System.out.println("===================================");
        SolutionPrinter.print(vrp, bestSolution, SolutionPrinter.Print.CONCISE);

        /*****************
         *  Plot solution
         *****************/
        new Plotter(vrp, bestSolution).plot("src/main/resources/solution_plot.png", "Best Solution, Cost: " + bestSolution.getCost());

        /*****************
         *  Render solution
         *****************/
        new GraphStreamViewer(vrp, bestSolution).labelWith(GraphStreamViewer.Label.ID).setRenderDelay(100).display();
    }


    /**
     * AUXILIARY METHOD FOR COST MATRIX COMPUTATION
     * @param vrpBuilder
     * @return
     */
    private static VehicleRoutingTransportCostsMatrix createMatrix(VehicleRoutingProblem.Builder vrpBuilder)
    {
        VehicleRoutingTransportCostsMatrix.Builder matrixBuilder = VehicleRoutingTransportCostsMatrix.Builder.newInstance(true);

        for (String from : vrpBuilder.getLocationMap().keySet())
        {
            for (String to : vrpBuilder.getLocationMap().keySet())
            {
                Coordinate fromCoord = vrpBuilder.getLocationMap().get(from);
                Coordinate toCoord = vrpBuilder.getLocationMap().get(to);
                //double distance = EuclideanDistanceCalculator.calculateDistance(fromCoord, toCoord);
                double distance = ( (GeoPointer) new GCJPointer(fromCoord.getX(), fromCoord.getY())).distance(new GCJPointer(toCoord.getX(), toCoord.getY())) ;
                // should get distance in meters (?)
                matrixBuilder.addTransportDistance(from, to, distance);
                //matrixBuilder.addTransportTime(from, to, (distance / 2.));
            }
        }
        return matrixBuilder.build();
    }
}


