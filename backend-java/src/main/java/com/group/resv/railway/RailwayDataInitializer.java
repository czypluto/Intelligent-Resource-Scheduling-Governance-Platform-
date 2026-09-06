package com.group.resv.railway;

import com.group.resv.railway.domain.Station;
import com.group.resv.railway.domain.Train;
import com.group.resv.railway.domain.TrainStop;
import com.group.resv.railway.domain.Trip;
import com.group.resv.railway.domain.TripClass;
import com.group.resv.railway.repo.StationRepository;
import com.group.resv.railway.repo.TrainRepository;
import com.group.resv.railway.repo.TrainStopRepository;
import com.group.resv.railway.repo.TripClassRepository;
import com.group.resv.railway.repo.TripRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 演示线路自愈播种：保证京沪 G101 及未来 30 天开行 + 席别库存都在（缺则补），并预热 Redis 余票。
 * 每次启动幂等执行，避免演示日期过期后无车可查。
 */
@Component
@Order(11)
public class RailwayDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RailwayDataInitializer.class);
    private static final int AHEAD_DAYS = 30;

    private final StationRepository stationRepository;
    private final TrainRepository trainRepository;
    private final TrainStopRepository trainStopRepository;
    private final TripRepository tripRepository;
    private final TripClassRepository tripClassRepository;
    private final RailwayStockService stockService;

    public RailwayDataInitializer(StationRepository stationRepository,
                                  TrainRepository trainRepository,
                                  TrainStopRepository trainStopRepository,
                                  TripRepository tripRepository,
                                  TripClassRepository tripClassRepository,
                                  RailwayStockService stockService) {
        this.stationRepository = stationRepository;
        this.trainRepository = trainRepository;
        this.trainStopRepository = trainStopRepository;
        this.tripRepository = tripRepository;
        this.tripClassRepository = tripClassRepository;
        this.stockService = stockService;
    }

    @Override
    public void run(ApplicationArguments args) {
        Train g101 = trainRepository.findByCode("G101").orElseGet(this::createTrain);
        ensureStations();
        if (trainStopRepository.findByTrainIdOrderBySeqAsc(g101.getId()).size() < 2) {
            rebuildStops(g101.getId());
        }
        int created = 0;
        for (int d = 0; d < AHEAD_DAYS; d++) {
            LocalDate date = LocalDate.now().plusDays(d);
            Trip trip = tripRepository.findByTrainIdAndTravelDate(g101.getId(), date).orElse(null);
            if (trip == null) {
                Trip t = new Trip();
                t.setTrainId(g101.getId());
                t.setTravelDate(date);
                trip = tripRepository.save(t);
                created++;
            }
            for (TripClass tc : List.of(
                    seat(trip.getId(), "二等座", 55300, 600),
                    seat(trip.getId(), "一等座", 93300, 100),
                    seat(trip.getId(), "商务座", 174800, 20))) {
                stockService.preheat(trip.getId(), tc.getSeatClass());
            }
        }
        if (created > 0) {
            log.info("演示线路 G101 已补开未来 {} 天（新增 {} 天）", AHEAD_DAYS, created);
        }
    }

    private Train createTrain() {
        Train t = new Train();
        t.setCode("G101");
        t.setName("北京南-上海虹桥");
        t.setKind("G");
        return trainRepository.save(t);
    }

    private void ensureStations() {
        station("BJP", "北京南");
        station("JNK", "济南西");
        station("NJH", "南京南");
        station("SHH", "上海虹桥");
    }

    private void rebuildStops(Long trainId) {
        trainStopRepository.deleteByTrainId(trainId);
        station("BJP", "北京南");
        Station bjn = station("BJP", "北京南");
        Station jnx = station("JNK", "济南西");
        Station njn = station("NJH", "南京南");
        Station shh = station("SHH", "上海虹桥");
        stop(trainId, 0, bjn.getId(), null, LocalTime.of(7, 0));
        stop(trainId, 1, jnx.getId(), LocalTime.of(8, 15), LocalTime.of(8, 18));
        stop(trainId, 2, njn.getId(), LocalTime.of(10, 5), LocalTime.of(10, 9));
        stop(trainId, 3, shh.getId(), LocalTime.of(11, 30), null);
    }

    private Station station(String code, String name) {
        return stationRepository.findByCode(code).orElseGet(() -> {
            Station s = new Station();
            s.setCode(code);
            s.setName(name);
            return stationRepository.save(s);
        });
    }

    private void stop(Long trainId, int seq, Long stationId, LocalTime arrive, LocalTime depart) {
        TrainStop st = new TrainStop();
        st.setTrainId(trainId);
        st.setSeq(seq);
        st.setStationId(stationId);
        st.setArriveTime(arrive);
        st.setDepartTime(depart);
        trainStopRepository.save(st);
    }

    private TripClass seat(Long tripId, String seatClass, long priceCents, int seats) {
        return tripClassRepository.findByTripIdAndSeatClass(tripId, seatClass).orElseGet(() -> {
            TripClass tc = new TripClass();
            tc.setTripId(tripId);
            tc.setSeatClass(seatClass);
            tc.setPriceCents(priceCents);
            tc.setTotalSeats(seats);
            return tripClassRepository.save(tc);
        });
    }
}
