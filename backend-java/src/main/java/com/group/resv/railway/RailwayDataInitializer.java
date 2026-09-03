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
 * 首次启动播种演示线路：京沪线 G101，未来 3 天开行 + 席别库存，并预热 Redis 余票。
 */
@Component
@Order(11)
public class RailwayDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RailwayDataInitializer.class);

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
        if (trainRepository.count() > 0) {
            return;
        }
        Station bjn = station("BJP", "北京南");
        Station jnx = station("JNK", "济南西");
        Station njn = station("NJH", "南京南");
        Station shh = station("SHH", "上海虹桥");

        Train g101 = new Train();
        g101.setCode("G101");
        g101.setName("北京南-上海虹桥");
        g101.setKind("G");
        trainRepository.save(g101);

        stop(g101.getId(), 0, bjn.getId(), null, LocalTime.of(7, 0));
        stop(g101.getId(), 1, jnx.getId(), LocalTime.of(8, 15), LocalTime.of(8, 18));
        stop(g101.getId(), 2, njn.getId(), LocalTime.of(10, 5), LocalTime.of(10, 9));
        stop(g101.getId(), 3, shh.getId(), LocalTime.of(11, 30), null);

        LocalDate today = LocalDate.now();
        for (int d = 0; d < 3; d++) {
            Trip trip = new Trip();
            trip.setTrainId(g101.getId());
            trip.setTravelDate(today.plusDays(d));
            tripRepository.save(trip);
            seat(trip.getId(), "二等座", 55300, 600);
            seat(trip.getId(), "一等座", 93300, 100);
            seat(trip.getId(), "商务座", 174800, 20);
        }

        // 预热未来 3 天余票
        for (int d = 0; d < 3; d++) {
            tripRepository.findByTrainIdAndTravelDate(g101.getId(), today.plusDays(d)).ifPresent(trip -> {
                for (TripClass tc : tripClassRepository.findByTripIdOrderByIdAsc(trip.getId())) {
                    stockService.preheat(trip.getId(), tc.getSeatClass());
                }
            });
        }
        log.info("已播种演示线路 G101（未来 3 天）");
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

    private void seat(Long tripId, String seatClass, long priceCents, int seats) {
        TripClass tc = new TripClass();
        tc.setTripId(tripId);
        tc.setSeatClass(seatClass);
        tc.setPriceCents(priceCents);
        tc.setTotalSeats(seats);
        tripClassRepository.save(tc);
    }
}
