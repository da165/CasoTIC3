import java.util.Arrays;
import java.util.Random;

public class SimuladorCaso3 {
    public static void main(String[] args) {
        System.out.println("=== INICIANDO SIMULADOR DE RECURSOS PARA MVs ===");        
        int nu = 3;           // Número de usuarios
        int base = 2;         // Número base de eventos
        int ng = 2;           // Número de gestores
        int tam1 = 5;         // Capacidad del buzón de entrada
        int tam2 = 3;         // Capacidad / umbral de la zona de espera
        int totalProc = 8;    // Número de procesadores
        int totalMemGiB = 16; // Memoria total en GiB
        int tamPaginaKiB = 4; // Tamaño de página en KiB
        int totalAlmGiB = 64; // Almacenamiento total en GiB
        int tamBloqueKiB = 8; // Tamaño de bloque en KiB

        // Instanciación de componentes compartidos
        BuzonEntrada buzon = new BuzonEntrada(tam1, nu);
        ZonaEspera zonaEspera = new ZonaEspera(tam2);
        ControladorProcesadores cProc = new ControladorProcesadores(totalProc);
        ControladorMemoria cMem = new ControladorMemoria(totalMemGiB, tamPaginaKiB);
        ControladorAlmacenamiento cAlm = new ControladorAlmacenamiento(totalAlmGiB, tamBloqueKiB);

        // Creación y arranque de hilos de Usuarios
        Usuario[] usuarios = new Usuario[nu];
        for (int i = 0; i < nu; i++) {
            usuarios[i] = new Usuario(i + 1, base, buzon, totalProc, totalMemGiB, totalAlmGiB);
            usuarios[i].start();
        }

        // Creación y arranque de hilos de Gestores de Solicitudes
        GestorSolicitudes[] gestores = new GestorSolicitudes[ng];
        for (int i = 0; i < ng; i++) {
            gestores[i] = new GestorSolicitudes(buzon, zonaEspera, cProc, cMem, cAlm);
            gestores[i].start();
        }

        // Esperar a que terminen los usuarios
        try {
            for (Usuario u : usuarios) {
                u.join();
            }
            // Esperar a que terminen los gestores
            for (GestorSolicitudes g : gestores) {
                g.join();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("=== SIMULACIÓN FINALIZADA CON ÉXITO ===");
        System.out.println("Buzón vacío: " + buzon.estaVacio());
        System.out.println("Zona de espera vacía: " + (zonaEspera.getTamano() == 0));
        System.out.println("Procesadores libres: " + cProc.estaTodoLibre());
        System.out.println("Memoria libre: " + cMem.estaTodoLibre());
        System.out.println("Almacenamiento libre: " + cAlm.estaTodoLibre());
    }
}


class Solicitud {
    private final String id;
    private final int procesadores;
    private final int memoriaGiB;
    private final int almacenamientoGiB;

    public Solicitud(String id, int procesadores, int memoriaGiB, int almacenamientoGiB) {
        this.id = id;
        this.procesadores = procesadores;
        this.memoriaGiB = memoriaGiB;
        this.almacenamientoGiB = almacenamientoGiB;
    }

    public String getId() { return id; }
    public int getProcesadores() { return procesadores; }
    public int getMemoriaGiB() { return memoriaGiB; }
    public int getAlmacenamientoGiB() { return almacenamientoGiB; }
}


class ControladorProcesadores {
    private final String[] asignaciones;

    public ControladorProcesadores(int totalProcesadores) {
        this.asignaciones = new String[totalProcesadores];
        Arrays.fill(asignaciones, null);
    }

    public synchronized int[] reservar(int cantidad, String mvId) {
        int libres = 0;
        for (String id : asignaciones) if (id == null) libres++;
        if (libres < cantidad) return null;

        int[] reservados = new int[cantidad];
        int idx = 0;
        for (int i = 0; i < asignaciones.length && idx < cantidad; i++) {
            if (asignaciones[i] == null) {
                asignaciones[i] = mvId;
                reservados[idx++] = i;
            }
        }
        return reservados;
    }

    public synchronized void liberar(int[] indices) {
        if (indices == null) return;
        for (int idx : indices) asignaciones[idx] = null;
    }

    public synchronized boolean estaTodoLibre() {
        for (String id : asignaciones) if (id != null) return false;
        return true;
    }
}

class ControladorMemoria {
    private final String[] paginas;
    private final int tamPaginaKiB;

    public ControladorMemoria(int totalGiB, int tamPaginaKiB) {
        this.tamPaginaKiB = tamPaginaKiB;
        long totalKiB = (long) totalGiB * 1024 * 1024;
        int numPaginas = (int) (totalKiB / tamPaginaKiB);
        this.paginas = new String[numPaginas];
        Arrays.fill(paginas, null);
    }

    public synchronized int[] reservar(int memoriaGiB, String mvId) {
        long reqKiB = (long) memoriaGiB * 1024 * 1024;
        int paginasNecesarias = (int) Math.ceil((double) reqKiB / tamPaginaKiB);

        int libres = 0;
        for (String id : paginas) if (id == null) libres++;
        if (libres < paginasNecesarias) return null;

        int[] reservados = new int[paginasNecesarias];
        int idx = 0;
        for (int i = 0; i < paginas.length && idx < paginasNecesarias; i++) {
            if (paginas[i] == null) {
                paginas[i] = mvId;
                reservados[idx++] = i;
            }
        }
        return reservados;
    }

    public synchronized void liberar(int[] indices) {
        if (indices == null) return;
        for (int idx : indices) paginas[idx] = null;
    }

    public synchronized boolean estaTodoLibre() {
        for (String id : paginas) if (id != null) return false;
        return true;
    }
}

class ControladorAlmacenamiento {
    private final String[] bloques;
    private final int tamBloqueKiB;

    public ControladorAlmacenamiento(int totalGiB, int tamBloqueKiB) {
        this.tamBloqueKiB = tamBloqueKiB;
        long totalKiB = (long) totalGiB * 1024 * 1024;
        int numBloques = (int) (totalKiB / tamBloqueKiB);
        this.bloques = new String[numBloques];
        Arrays.fill(bloques, null);
    }

    public synchronized int[] reservar(int almacenamientoGiB, String mvId) {
        long reqKiB = (long) almacenamientoGiB * 1024 * 1024;
        int bloquesNecesarios = (int) Math.ceil((double) reqKiB / tamBloqueKiB);

        int libres = 0;
        for (String id : bloques) if (id == null) libres++;
        if (libres < bloquesNecesarios) return null;

        int[] reservados = new int[bloquesNecesarios];
        int idx = 0;
        for (int i = 0; i < bloques.length && idx < bloquesNecesarios; i++) {
            if (bloques[i] == null) {
                bloques[i] = mvId;
                reservados[idx++] = i;
            }
        }
        return reservados;
    }

    public synchronized void liberar(int[] indices) {
        if (indices == null) return;
        for (int idx : indices) bloques[idx] = null;
    }

    public synchronized boolean estaTodoLibre() {
        for (String id : bloques) if (id != null) return false;
        return true;
    }
}

class MaquinaVirtual extends Thread {
    private final Solicitud solicitud;
    private final ZonaEspera zonaEspera;
    private final ControladorProcesadores cProc;
    private final ControladorMemoria cMem;
    private final ControladorAlmacenamiento cAlm;

    public MaquinaVirtual(Solicitud solicitud, ZonaEspera zonaEspera,
                          ControladorProcesadores cProc, ControladorMemoria cMem, ControladorAlmacenamiento cAlm) {
        this.solicitud = solicitud;
        this.zonaEspera = zonaEspera;
        this.cProc = cProc;
        this.cMem = cMem;
        this.cAlm = cAlm;
    }

    @Override
    public void run() {
        int[] procAsignados = null;
        int[] memAsignada = null;
        int[] almAsignado = null;
        boolean configurada = false;

        while (!configurada) {
            procAsignados = cProc.reservar(solicitud.getProcesadores(), solicitud.getId());
            if (procAsignados != null) {
                memAsignada = cMem.reservar(solicitud.getMemoriaGiB(), solicitud.getId());
                if (memAsignada != null) {
                    almAsignado = cAlm.reservar(solicitud.getAlmacenamientoGiB(), solicitud.getId());
                    if (almAsignado != null) {
                        configurada = true;
                    } else {
                        cMem.liberar(memAsignada);
                        cProc.liberar(procAsignados);
                        memAsignada = null;
                        procAsignados = null;
                    }
                } else {
                    cProc.liberar(procAsignados);
                    procAsignados = null;
                }
            }

            if (!configurada) {
                try {
                    synchronized (zonaEspera) {
                        zonaEspera.ingresar(this);
                        zonaEspera.esperarNuevosRecursos();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }

        try {
            int tiempoEjecucion = 1000 + new Random().nextInt(2000); // Acortado para pruebas rápidas
            Thread.sleep(tiempoEjecucion);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            cAlm.liberar(almAsignado);
            cMem.liberar(memAsignada);
            cProc.liberar(procAsignados);
            zonaEspera.notificarCambioRecursos();
        }
    }
}
