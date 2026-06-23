import java.util.*;

public class EmergencyRouteSystem {

    // Struktur Data Node/Edge
    static class Edge {
        String target;
        int weight;

        Edge(String target, int weight) {
            this.target = target;
            this.weight = weight;
        }
    }

    // Graph data untuk Pejalan Kaki dan Motor
    static Map<String, List<Edge>> walkGraph = new HashMap<>();
    static Map<String, List<Edge>> motorGraph = new HashMap<>();
    
    // Daftar lokasi unik yang diurutkan secara alfabetis
    static List<String> locationList = new ArrayList<>();

    // Definisi Hambatan
    static final Map<Integer, String> OBSTACLE_NAMES = new HashMap<>() {{
        put(1, "Normal");
        put(2, "Jalan Macet");
        put(3, "Penebangan Pohon");
        put(4, "Pohon Tumbang");
        put(5, "Perbaikan Jalan");
        put(6, "Kegiatan Kampus");
        put(7, "Kecelakaan Kendaraan");
    }};

    // Tambahan beban waktu dari hambatan (Dikonversi dari Menit ke Detik agar sinkron saat Dijkstra)
    static final Map<Integer, Integer> OBSTACLE_WEIGHTS = new HashMap<>() {{
        put(1, 0 * 60);  // Normal
        put(2, 5 * 60);  // Jalan Macet (5 mnt -> 300 dtk)
        put(3, 3 * 60);  // Penebangan Pohon (3 mnt -> 180 dtk)
        put(4, 5 * 60);  // Pohon Tumbang (5 mnt -> 300 dtk)
        put(5, 4 * 60);  // Perbaikan Jalan (4 mnt -> 240 dtk)
        put(6, 1 * 60);  // Kegiatan Kampus (1 mnt -> 60 dtk)
        put(7, 6 * 60);  // Kecelakaan Kendaraan (6 mnt -> 360 dtk)
    }};

    public static void main(String[] args) {
        // Load data awal
        loadWalkDataset();
        loadMotorDataset();
        generateLocationList();

        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.println("==================================================");
            System.out.println("            EMERGENCY ROUTE SYSTEM UNS            ");
            System.out.println("==================================================");
            System.out.println("1. Cari Rute Tercepat");
            System.out.println("2. Tampilkan Semua Lokasi");
            System.out.println("3. Keluar");
            System.out.println("==================================================");
            System.out.print("Pilih Menu : ");
            
            int menu = getValidIntInput(scanner);
            System.out.println();

            if (menu == 3) {
                System.out.println("Terima kasih telah menggunakan Emergency Route System UNS.");
                break;
            } else if (menu == 2) {
                printAllLocations();
            } else if (menu == 1) {
                prosesPencarianRute(scanner);
            } else {
                System.out.println("Pilihan menu tidak valid!\n");
            }
        }
        scanner.close();
    }

    private static void prosesPencarianRute(Scanner scanner) {
        printAllLocations();

        System.out.print("Masukkan lokasi awal : ");
        String startInput = scanner.nextLine().trim();
        String startLocation = matchLocation(startInput);

        if (startLocation == null) {
            System.out.println("Lokasi awal tidak ditemukan atau tidak sesuai daftar!\n");
            return;
        }

        String destination = "Medical Center";

        System.out.println("\n==================================================");
        System.out.println("PILIH JENIS TRANSPORTASI");
        System.out.println("==================================================");
        System.out.println("1. Jalan Kaki");
        System.out.println("2. Kendaraan Bermotor");
        System.out.print("Pilihan : ");
        int transportOption = getValidIntInput(scanner);

        if (transportOption != 1 && transportOption != 2) {
            System.out.println("Pilihan transportasi tidak valid!\n");
            return;
        }

        String mode = (transportOption == 1) ? "WALK" : "MOTOR";

        String lokasiHambatan = "";
        int obstacleOption = 1;

        if (mode.equals("MOTOR")) {
            System.out.println("\n==================================================");
            System.out.println("PILIH KONDISI JALAN");
            System.out.println("==================================================");
            System.out.println("1. Normal");
            System.out.println("2. Jalan Macet");
            System.out.println("3. Penebangan Pohon");
            System.out.println("4. Pohon Tumbang");
            System.out.println("5. Perbaikan Jalan");
            System.out.println("6. Kegiatan Kampus");
            System.out.println("7. Kecelakaan Kendaraan");
            System.out.print("Pilihan : ");
            obstacleOption = getValidIntInput(scanner);

            if (!OBSTACLE_WEIGHTS.containsKey(obstacleOption)) {
                System.out.println("Pilihan kondisi jalan tidak valid!\n");
                return;
            }

            if (obstacleOption != 1) {
                System.out.print("Masukkan lokasi spesifik yang terkena hambatan (Contoh: Pascasarjana): ");
                String obstacleInput = scanner.nextLine().trim();
                lokasiHambatan = matchLocation(obstacleInput);
                
                if (lokasiHambatan == null) {
                    System.out.println("Lokasi hambatan tidak valid. Hambatan dibatalkan.\n");
                    obstacleOption = 1;
                }
            }
        }

        hitungDanCetakRute(startLocation, destination, mode, obstacleOption, lokasiHambatan);
    }

    // Fungsi helper untuk mengubah detik ke menit bulat ke atas
    private static int konversiDetikKeMenitBulatAtas(int detik) {
        return (int) Math.ceil((double) detik / 60.0);
    }

    private static void hitungDanCetakRute(String start, String target, String mode, int obstacleOption, String lokasiHambatan) {
        
        // 1. Mendapatkan rute tercepat kondisi murni normal (Jalur A) - Satuan Detik
        List<String> normalPath = new ArrayList<>();
        int waktuDasarAsliDetik = runningDijkstraGetPath(start, target, mode, 1, "", normalPath);
        
        // Cek apakah rute normal (Jalur A) sebenarnya melewati lokasi hambatan yang diinput user
        boolean ruteNormalTerbawaHambatan = false;
        if (obstacleOption != 1 && mode.equals("MOTOR") && !lokasiHambatan.isEmpty()) {
            for (String node : normalPath) {
                if (node.equalsIgnoreCase(lokasiHambatan)) {
                    ruteNormalTerbawaHambatan = true;
                    break;
                }
            }
        }

        List<String> finalPath = new ArrayList<>();
        int waktuDasarJalurBaruDetik = 0;
        int tambahanHambatanDetik = 0;
        boolean ruteBeralih = false;

        // Kalau ada hambatan DAN rute normal ternyata melewati lokasi hambatan tersebut -> Cari Jalur B (Memutar)
        if (ruteNormalTerbawaHambatan) {
            waktuDasarJalurBaruDetik = runningDijkstraGetPath(start, target, mode, obstacleOption, lokasiHambatan, finalPath);
            tambahanHambatanDetik = OBSTACLE_WEIGHTS.get(obstacleOption);
            ruteBeralih = true;
            
            if (waktuDasarJalurBaruDetik >= 999999) { // Nilai disesuaikan skala detik batas atas
                System.out.println("\n[Peringatan] Rute tidak ditemukan karena jalan alternatif terblokir sepenuhnya!");
                return;
            }
        } else {
            // JIKA tidak ada hambatan ATAU ada hambatan tapi rute kita TIDAK melewati jalan rusak itu, jalurnya TETAP normal
            finalPath = new ArrayList<>(normalPath);
            waktuDasarJalurBaruDetik = waktuDasarAsliDetik;
            tambahanHambatanDetik = 0; 
        }

        int totalWaktuJalurAwalDetik = waktuDasarAsliDetik + tambahanHambatanDetik;
        int estimasiJalurSekarangDetik = waktuDasarJalurBaruDetik + tambahanHambatanDetik;

        // Output Ringkasan Awal Sebelum Blok Hasil Utama (Konversi Hasil Akhir ke Menit Bulat Atas)
        System.out.println("\nWaktu Dasar        : " + konversiDetikKeMenitBulatAtas(waktuDasarAsliDetik) + " menit");
        System.out.println("Total Waktu        : " + konversiDetikKeMenitBulatAtas(totalWaktuJalurAwalDetik) + " menit");
        System.out.println("Tambahan Hambatan  : " + konversiDetikKeMenitBulatAtas(tambahanHambatanDetik) + " menit");
        if (ruteBeralih) {
            System.out.println("Waktu Estimasi Jalur Sekarang: " + konversiDetikKeMenitBulatAtas(estimasiJalurSekarangDetik) + " menit");
        }

        // Blok Cetak Hasil Rekomendasi Rute Akhir
        System.out.println("\n==================================================");
        System.out.println("                 HASIL REKOMENDASI RUTE             ");
        System.out.println("==================================================");
        System.out.println("Lokasi Awal        : " + start);
        System.out.println("Tujuan             : " + target);
        System.out.println("Transportasi       : " + (mode.equals("WALK") ? "Jalan Kaki" : "Kendaraan Bermotor"));
        
        // Status Kondisi Jalan disesuaikan secara logis
        if (obstacleOption != 1 && !ruteNormalTerbawaHambatan && mode.equals("MOTOR")) {
            System.out.println("Kondisi Jalan      : Ada " + OBSTACLE_NAMES.get(obstacleOption) + " di " + lokasiHambatan + " (Tidak Melewati Rute Anda / Normal)");
        } else {
            System.out.println("Kondisi Jalan      : " + OBSTACLE_NAMES.get(obstacleOption) + (obstacleOption != 1 ? " di " + lokasiHambatan : ""));
        }

        System.out.println("\nRute Tercepat Sekarang : ");
        System.out.println(String.join(" -> ", finalPath));
        System.out.println("\nWaktu Dasar Normal : " + konversiDetikKeMenitBulatAtas(waktuDasarAsliDetik) + " menit");
        System.out.println("Tambahan Hambatan  : " + konversiDetikKeMenitBulatAtas(tambahanHambatanDetik) + " menit");
        
        if (ruteBeralih) {
            System.out.println("Waktu Estimasi Jalur Sekarang: " + konversiDetikKeMenitBulatAtas(estimasiJalurSekarangDetik) + " menit (Rute Memutar)");
        } else {
            System.out.println("Total Waktu        : " + konversiDetikKeMenitBulatAtas(totalWaktuJalurAwalDetik) + " menit");
        }
        System.out.println("==================================================\n");
    }

    // Fungsi inti Dijkstra yang mengembalikan Waktu Dasar MURNI dalam satuan DETIK
    private static int runningDijkstraGetPath(String start, String target, String mode, int obstacleOption, String lokasiHambatan, List<String> outPath) {
        Map<String, List<Edge>> graph = mode.equals("WALK") ? walkGraph : motorGraph;
        Map<String, Integer> distances = new HashMap<>();
        Map<String, String> parentNodes = new HashMap<>();
        PriorityQueue<Edge> pq = new PriorityQueue<>(Comparator.comparingInt(e -> e.weight));

        for (String node : locationList) distances.put(node, Integer.MAX_VALUE);
        distances.put(start, 0);
        pq.add(new Edge(start, 0));

        while (!pq.isEmpty()) {
            Edge current = pq.poll();
            String u = current.target;
            if (u.equals(target)) break;
            if (current.weight > distances.get(u)) continue;

            for (Edge edge : graph.getOrDefault(u, new ArrayList<>())) {
                String v = edge.target;
                
                int penaltyBlokir = 0;
                if (mode.equals("MOTOR") && obstacleOption != 1 && v.equalsIgnoreCase(lokasiHambatan)) {
                    penaltyBlokir = 999999; // Disesuaikan dengan batas atas detik
                }

                int newDist = distances.get(u) + edge.weight + penaltyBlokir;
                if (newDist < 0) newDist = Integer.MAX_VALUE;

                if (newDist < distances.get(v)) {
                    distances.put(v, newDist);
                    parentNodes.put(v, u);
                    pq.add(new Edge(v, newDist));
                }
            }
        }

        if (distances.get(target) >= 999999) {
            return Integer.MAX_VALUE;
        }

        // Rekonstruksi rute
        String step = target;
        while (step != null) {
            outPath.add(0, step);
            step = parentNodes.get(step);
        }

        // Hitung total bobot dasar murni dalam detik
        int totalBaseWeight = 0;
        for (int i = 0; i < outPath.size() - 1; i++) {
            String u = outPath.get(i);
            String v = outPath.get(i + 1);
            for (Edge edge : graph.get(u)) {
                if (edge.target.equals(v)) {
                    totalBaseWeight += edge.weight;
                    break;
                }
            }
        }
        return totalBaseWeight;
    }

    private static void printAllLocations() {
        System.out.println("==================================================");
        System.out.println("                  DAFTAR LOKASI                   ");
        System.out.println("==================================================");
        for (int i = 0; i < locationList.size(); i++) {
            System.out.printf("%2d. %s\n", (i + 1), locationList.get(i));
        }
        System.out.println("==================================================\n");
    }

    private static void generateLocationList() {
        Set<String> uniqueLocations = new TreeSet<>();
        uniqueLocations.addAll(walkGraph.keySet());
        uniqueLocations.addAll(motorGraph.keySet());
        locationList.addAll(uniqueLocations);
    }

    private static String matchLocation(String input) {
        for (String loc : locationList) {
            if (loc.equalsIgnoreCase(input)) {
                return loc;
            }
        }
        return null;
    }

    private static int getValidIntInput(Scanner scanner) {
        while (!scanner.hasNextInt()) {
            System.out.print("Input harus berupa nomor! Masukkan kembali: ");
            scanner.next();
        }
        int val = scanner.nextInt();
        scanner.nextLine(); 
        return val;
    }

    public static void addWalkEdge(String source, String target, int weight) {
        walkGraph.computeIfAbsent(source, k -> new ArrayList<>()).add(new Edge(target, weight));
        walkGraph.computeIfAbsent(target, k -> new ArrayList<>()).add(new Edge(source, weight));
    }

    public static void addMotorEdge(String source, String target, int weight) {
        motorGraph.computeIfAbsent(source, k -> new ArrayList<>()).add(new Edge(target, weight));
        if (!motorGraph.containsKey(target)) {
            motorGraph.put(target, new ArrayList<>());
        }
    }

    // DATASET PEJALAN KAKI
    public static void loadWalkDataset() {
        addWalkEdge("Gerbang Depan", "Danau", 240);
        addWalkEdge("Danau", "FP", 120);
        addWalkEdge("FP", "FMIPA", 180);
        addWalkEdge("Gerbang Depan", "Perpus", 180);
        addWalkEdge("Perpus", "Audit", 60);
        addWalkEdge("Audit", "Rektorat", 60);
        addWalkEdge("Rektorat", "DTIK", 120);
        addWalkEdge("FMIPA", "DTIK", 120);
        addWalkEdge("FMIPA", "PSIKO", 240);
        addWalkEdge("DTIK", "PSIKO", 120);
        addWalkEdge("DTIK", "Pascasarjana", 180);
        addWalkEdge("PSIKO", "FK", 120);
        addWalkEdge("PSIKO", "BRI Corner", 120);
        addWalkEdge("BRI Corner", "Pascasarjana", 60);
        addWalkEdge("Pascasarjana", "FKIP A", 120);
        addWalkEdge("FKIP A", "Stadion", 120);
        addWalkEdge("Stadion", "Medical Center", 180);
        addWalkEdge("Medical Center", "NH", 60);
        addWalkEdge("NH", "Z Corner", 60);
        addWalkEdge("Z Corner", "Gerbang Belakang", 60);
        addWalkEdge("Medical Center", "Gerbang Belakang", 120);
    }

    // DATASET KENDARAAN BERMOTOR
    public static void loadMotorDataset() {
        // ==================== AREA GERBANG UTAMA & REKTORAT (ATAS) ====================
        addMotorEdge("Gerbang Depan", "Rektorat", 90);      
        addMotorEdge("Rektorat", "Gerbang Depan", 90);      

        addMotorEdge("FP", "Danau", 15);                    
        addMotorEdge("Danau", "Rektorat", 45);              

        addMotorEdge("Rektorat", "Audit", 3);               
        addMotorEdge("Audit", "Perpus", 6);                 
        addMotorEdge("Perpus", "DTIK", 15);                 

        // ==================== AREA LINGKAR BARAT & AKSES FMIPA ====================
        addMotorEdge("FK", "FAPET", 30);                    
        addMotorEdge("FAPET", "FK", 30);     
        addMotorEdge("FAPET", "FP", 45);                   
        addMotorEdge("FP", "FAPET", 45);     
        addMotorEdge("FP", "DTIK", 52);  

        addMotorEdge("FAPET", "FMIPA", 52);   

        addMotorEdge("FMIPA", "DTIK", 10);    
        addMotorEdge("FMIPA", "FAPET", 45); 

        addMotorEdge("FAPET", "DTIK", 60);   
        addMotorEdge("DTIK", "FAPET", 60);   

        addMotorEdge("FK", "PSIKO", 40);                    
        addMotorEdge("PSIKO", "FK", 40);

        addMotorEdge("FK", "GOR", 50);                     
        addMotorEdge("PSIKO", "GOR", 50);                  
        addMotorEdge("GOR", "Medical Center", 30);          

        // ==================== AREA TENGAH (PSIKO, DTIK, PASCASARJANA) ====================
        addMotorEdge("DTIK", "Pascasarjana", 75);          
        addMotorEdge("Pascasarjana", "DTIK", 75);          
        addMotorEdge("Pascasarjana", "PSIKO", 65);         

        // ==================== AREA UTAMA SELATAN (FKIP A, NH, Z CORNER) ====================
        addMotorEdge("Pascasarjana", "FKIP A", 34);        
        addMotorEdge("FKIP A", "Pascasarjana", 34);        

        addMotorEdge("FKIP A", "Medical Center", 25);       

        addMotorEdge("FKIP A", "NH", 16);                   
        addMotorEdge("NH", "FKIP A", 16);                   

        addMotorEdge("NH", "Z Corner", 6);                  
        addMotorEdge("Z Corner", "NH", 6);                  

        addMotorEdge("Z Corner", "Medical Center", 15);    
        addMotorEdge("Medical Center", "Z Corner", 15);    

        addMotorEdge("Gerbang Belakang", "Medical Center", 30); 
        addMotorEdge("Z Corner", "Gerbang Belakang", 3);        
        addMotorEdge("FKIP A", "Z Corner", 20);                          

        // ==================== LINGKARAN SATU ARAH STADION ====================
        addMotorEdge("Medical Center", "Stadion", 30);      
        addMotorEdge("Stadion", "BRI Corner", 25);               
        
        addMotorEdge("BRI Corner", "PSIKO", 33);            
        addMotorEdge("BRI Corner", "FKIP A", 20);                
    }
}