import com.google.gson.*;
import okhttp3.*;

import java.io.*;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.Month;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static java.time.format.DateTimeFormatter.ISO_INSTANT;

public class DoctorAppointmentScheduler {
    // Constants
    final String BASE_URL = "http://scheduling-interview-2021-265534043.us-west-2.elb.amazonaws.com/api/Scheduling/";
    final String AUTH_TOKEN = "fe9fdf62-7ff7-4aad-bb2d-64d72b5b4c4a";
    final OkHttpClient client = new OkHttpClient();

    final int YEAR = 2021;

    // Variables
    Map<String, ArrayList<Integer>> appointmentsDoctorMap = new HashMap<>();
    Map<Integer, ArrayList<String>> personAppointmentMap = new HashMap<>();

    public static void main(String[] args) {
        // Create instance of DoctorAppointmentScheduler class and call the run method
        DoctorAppointmentScheduler doctorAppointmentScheduler = new DoctorAppointmentScheduler();
        doctorAppointmentScheduler.run();
    }

    private void run() {
        //Hit the Start endpoint to start the run and get the schedule to be stored and used
        resetSchedule();
        getSchedule();

        // Continue to loop and handle all appointment requests
        while (true) {
            Response response = getAppointment();

            // If the response code is 204, then all appointments have been handle and program can end
            if (response.code() == 204) break;

            try {
                scheduleAppointment(response.body().string());
                System.out.println("Appointment Scheduled!");
            } catch (IOException e) {
                System.out.println(e.getMessage());
                System.out.println("Problem parsing json body");
            }
        }

        System.out.println("All appointments scheduled! Thanks for using the Doctor Appointment Scheduler!");
    }


    /*
        All API calls
    */
    private void resetSchedule() {
        try {
            //Create post request to start the run and allow the schedule endpoint to work
            HttpUrl.Builder urlBuilder = HttpUrl.parse(BASE_URL + "Start").newBuilder();
            urlBuilder.addQueryParameter("token", AUTH_TOKEN);

            String url = urlBuilder.build().toString();
            RequestBody reqbody = RequestBody.create(null, new byte[0]);

            Request request = new Request.Builder()
                    .url(url)
                    .method("POST", reqbody)
                    .header("Content-Length", "0")
                    .build();

            client.newCall(request).execute();
        } catch (IOException e) {
            System.out.println(e.getMessage());
            System.out.println("API error: error using api to reset schedule run. Please rerun program.");
            throw new RuntimeException(e);
        }
    }

    private void getSchedule() {
        try {
            // Create GET request and get all the current appointments scheduled
            HttpUrl.Builder urlBuilder = HttpUrl.parse(BASE_URL + "Schedule").newBuilder();
            urlBuilder.addQueryParameter("token", AUTH_TOKEN);
            String url = urlBuilder.build().toString();

            Request request = new Request.Builder()
                    .url(url)
                    .build();
            Response response = client.newCall(request).execute();

            // Pass the json string to be parsed and stored
            parseScheduleList(response.body().string());
        } catch (IOException e) {
            System.out.println(e.getMessage());
            System.out.println("API error: error using api to get initial schedule. Please rerun program.");
            throw new RuntimeException(e);
        }
    }

    private Response getAppointment() {
        try {
            // Create GET request and get all the current appointments scheduled
            HttpUrl.Builder urlBuilder = HttpUrl.parse(BASE_URL + "AppointmentRequest").newBuilder();
            urlBuilder.addQueryParameter("token", AUTH_TOKEN);
            String url = urlBuilder.build().toString();

            Request request = new Request.Builder()
                    .url(url)
                    .build();
            Response response = client.newCall(request).execute();

            return response;
        } catch (IOException e) {
            System.out.println(e.getMessage());
            System.out.println("API error: error using api to get appointment. Please rerun program.");
            throw new RuntimeException(e);
        }
    }

    private void postAppointment(Integer doctorId, Integer personId, String time, boolean isNew, Integer requestId) {
        try {
            // Create GET request and get all the current appointments scheduled
            HttpUrl.Builder urlBuilder = HttpUrl.parse(BASE_URL + "Schedule").newBuilder();
            urlBuilder.addQueryParameter("token", AUTH_TOKEN);
            String url = urlBuilder.build().toString();

            RequestBody formBody = new FormBody.Builder()
                    .add("doctorId", String.valueOf(doctorId))
                    .add("personId", String.valueOf(personId))
                    .add("appointmentTime", time)
                    .add("isNewPatientAppointment", String.valueOf(isNew))
                    .add("requestId", String.valueOf(requestId))
                    .build();

            Request request = new Request.Builder()
                    .url(url)
                    .post(formBody)
                    .build();

            client.newCall(request).execute();
        } catch (IOException e) {
            System.out.println(e.getMessage());
            System.out.println("API error: error using api to post appointment. Please rerun program.");
            throw new RuntimeException(e);
        }
    }


    /*
         Scheduling Methods
    */
    private void scheduleAppointment(String request) {
        // Parse Json and get all the appointment values
        JsonObject jsonObject = new JsonParser().parse(request).getAsJsonObject();
        Integer requestId = jsonObject.get("requestId").getAsInt();
        Integer personId = jsonObject.get("personId").getAsInt();
        boolean isNew = jsonObject.get("isNew").getAsBoolean();

        ArrayList<String> prefDays = new ArrayList<>();
        JsonArray days = jsonObject.get("preferredDays").getAsJsonArray();
        if (days != null) {
            for (JsonElement day: days){
                prefDays.add(day.getAsString());
            }
        }

        // New appointments are limited on certain times and should go first
        // TODO: implement method to only schedule new appointment for 3pm or 4pm

        // check to see if preferred days work
        for (String day: prefDays) {
            try {
                if (validDay(day, personId, isNew, requestId)) return;
            } catch (ParseException e) {
                System.out.println(e.getMessage());
            }
        }

        // Start with the earliest time and find a valid time for person
        findValidTime(personId, isNew, requestId);
    }

    private void findValidTime(Integer personId, boolean isNew, Integer requestId) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss[.SSS]X", Locale.ENGLISH);

        Calendar c = Calendar.getInstance();
        c.setTimeZone(TimeZone.getTimeZone("UTC"));
        c.set(YEAR, Calendar.NOVEMBER, 0, 8, 0);

        while (true) {
            // If it is past December, then no available appointments and break out of loop
            if (c.getTime().getMonth() == Calendar.JANUARY) {
                System.out.println("No available appointments for requestId: " + String.valueOf(requestId));
                break;
            }

            // If Saturday or Sunday, increment day
            if (c.getTime().getDay() == Calendar.SATURDAY || c.getTime().getDay() == Calendar.SUNDAY) {
                c.add(Calendar.DAY_OF_WEEK, +1);
            }

            // If it is 4:00pm add, 16 hours
            if (c.getTime().getHours() == 16) {
                c.add(Calendar.HOUR_OF_DAY, +16);
            }

            // Check to see if this is a valid day; if so, return from function
            try {
                if (validDay(sdf.format(c.getTime()), personId, isNew, requestId)) return;
            } catch (ParseException e) {
                System.out.println(e.getMessage());
                System.out.println("Problem with parsing day");
            }
        }
    }

    private boolean validDay(String day, Integer personId, boolean isNew, Integer requestId) throws ParseException {
        if (appointmentsDoctorMap.containsKey(day)) {
            ArrayList<Integer> doctors = appointmentsDoctorMap.get(day);
            if (doctors.size() < 3 && atLeastAWeek(day, personId)) {
                for (int i = 1; i < 4; i++) {
                    if (!doctors.contains(i)) {
                        postAppointment(i, personId, day, isNew, requestId);
                        appointmentsDoctorMap.get(day).add(i);

                        if (!personAppointmentMap.containsKey(personId)) {
                            personAppointmentMap.put(personId, new ArrayList<String>());
                        }
                        personAppointmentMap.get(personId).add(day);
                    }
                }
            } else {
                return false;
            }
        } else {
            postAppointment(1, personId, day, isNew, requestId);
            appointmentsDoctorMap.put(day, new ArrayList<Integer>());
            appointmentsDoctorMap.get(day).add(1);

            if (!personAppointmentMap.containsKey(personId)) {
                personAppointmentMap.put(personId, new ArrayList<String>());
            }
            personAppointmentMap.get(personId).add(day);
            return true;
        }

        return false;
    }

    private boolean atLeastAWeek(String day, Integer personId) throws ParseException {
        if (!personAppointmentMap.containsKey(personId)) {
            return true;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss[.SSS]X", Locale.ENGLISH);
        Date currDay = sdf.parse(day);

        // Loop through already scheduled days to see if they are more than one week apart
        for (String scheduledDay: personAppointmentMap.get(personId)) {
            Date tempDay = sdf.parse(scheduledDay);
            long diffInMillies = Math.abs(tempDay.getTime() - currDay.getTime());
            long diff = TimeUnit.DAYS.convert(diffInMillies, TimeUnit.MILLISECONDS);

            if (Math.abs(diff) < 7) {
                return false;
            }
        }

        return true;
    }


    /*
        Helper Methods
    */
    private void parseScheduleList(String jsonStr)  {
        JsonArray appointmentList = new JsonParser().parse(jsonStr).getAsJsonArray();
        for (JsonElement appointment : appointmentList) {

            //For each appointment, store data into the appointmentDoctorMap and personAppointmentMap
            JsonObject jsonObject = appointment.getAsJsonObject();
            Integer doctorId = jsonObject.get("doctorId").getAsInt();
            Integer personId = jsonObject.get("personId").getAsInt();
            String time = jsonObject.get("appointmentTime").getAsString();

            if (!appointmentsDoctorMap.containsKey(time)) {
                appointmentsDoctorMap.put(time, new ArrayList<Integer>());
            }
            appointmentsDoctorMap.get(time).add(doctorId);

            if (!personAppointmentMap.containsKey(personId)) {
                personAppointmentMap.put(personId, new ArrayList<String>());
            }
            personAppointmentMap.get(personId).add(time);
        }
    }
}
