package modelos;

import controlador.ControladorSimulacion;
import java.util.Comparator;
import micelaneos.*;

public class Planificador {
    private List readyList;
    private List blockedList;
    private List exitList;
    private List allProcessList;
    private List suspendedReadyList;
    private List suspendedBlockedList;
    private ControladorSimulacion controlador;
    private EventLogger logger;
    public int selectedAlgorithm;

    public Planificador(List readyList, List blockedList, List exitList, List allProcess, 
                       List suspReadyList, List suspBlockList, ControladorSimulacion controlador) {
        this.controlador = controlador;
        this.readyList = readyList;
        this.blockedList = blockedList;
        this.exitList = exitList;
        this.allProcessList = allProcess;
        this.suspendedReadyList = suspReadyList;
        this.suspendedBlockedList = suspBlockList;
        this.logger = new EventLogger();
    }

    public int getSelectedAlgorithm() {
        return selectedAlgorithm;
    }

    public List getReadyList() {
        return readyList;
    }

    public void setSelectedAlgorithm(int selectedAlgorithm) {
        this.selectedAlgorithm = selectedAlgorithm;
    }
    
    public Proceso getProcess(){
        Proceso output = null;
        if(this.readyList.isEmpty()){
            if(selectedAlgorithm != controlador.getPolitica()){
                selectedAlgorithm = controlador.getPolitica();
                sortReadyQueue(selectedAlgorithm);
            }
            
            Nodo pAux = this.readyList.getHead();
            this.readyList.delete(pAux);
            output = (Proceso) pAux.getValue();
            output.setEstado("Ejecucion");
            
            logger.logEvent("Procesador selecciona Proceso " + output.getNombre() + " (ID: " + output.getId() + ")");
        }
        
        this.updateReadyList();
        this.updateProcessList();
        
        if(output == null){
            System.out.println("process null");
        }
        return output;    
    }
    
    private void sortReadyQueue(int schedulingAlgorithm) {
        switch (schedulingAlgorithm) {
            case 0: // FCFS
                readyList = sortByWaitingTime(readyList);
                logger.logEvent("Cambio de algoritmo a FCFS");
                break;
            case 1: // Round Robin
                readyList = sortByWaitingTime(readyList);
                logger.logEvent("Cambio de algoritmo a Round Robin");
                break;
            case 2: // SPN
                readyList = sortByDuration(readyList);
                logger.logEvent("Cambio de algoritmo a SPN");
                break;
            case 3: // SRT
                readyList = sortByRemainingTime(readyList);
                logger.logEvent("Cambio de algoritmo a SRT");
                break;
            case 4: // HRRN
                readyList = sortByHRR(readyList);
                logger.logEvent("Cambio de algoritmo a HRRN");
                break;
            case 5: // FB
                readyList = sortByFeedback(readyList);
                logger.logEvent("Cambio de algoritmo a Feedback");
                break;
        }
    }

    private List sortByWaitingTime(List list) {
        return bubbleSort(list, (p1, p2) -> Integer.compare(((Proceso) p2).getTiempoEspera(), ((Proceso) p1).getTiempoEspera()));
    }
    
    private List sortByDuration(List list) {
        return bubbleSort(list, (p1, p2) -> Integer.compare(((Proceso) p1).getInstrucciones(), ((Proceso) p2).getInstrucciones()));
    }

    private List sortByRemainingTime(List list) {
        return bubbleSort(list, (p1, p2) -> Integer.compare(
                ((Proceso) p1).getInstrucciones() - ((Proceso) p1).getPc(),
                ((Proceso) p2).getInstrucciones() - ((Proceso) p2).getPc()
        ));
    }

    private List sortByHRR(List list) {
        return bubbleSort(list, (p1, p2) -> Double.compare(getHRR((Proceso) p2), getHRR((Proceso) p1)));
    }

    private List sortByFeedback(List list) {
        return bubbleSort(list, (p1, p2) -> Integer.compare(
            ((Proceso) p2).getTiempoEspera(),
            ((Proceso) p1).getTiempoEspera()
        ));
    }

    private double getHRR(Proceso p) {
        int tiempoServicio = p.getInstrucciones();
        if(tiempoServicio == 0) return 0;
        return (p.getTiempoEspera() + tiempoServicio) / (double) tiempoServicio;
    }

    private List bubbleSort(List list, Comparator comparator) {
        if (list.getSize() <= 1) return list;

        boolean swapped;
        do {
            swapped = false;
            Nodo current = list.getHead();
            while (current != null && current.getpNext() != null) {
                if (comparator.compare(current.getValue(), current.getpNext().getValue()) > 0) {
                    Object temp = current.getValue();
                    current.setValue(current.getpNext().getValue());
                    current.getpNext().setValue(temp);
                    swapped = true;
                }
                current = current.getpNext();
            }
        } while (swapped);

        return list;
    }

    public boolean ifSRT(Proceso process){
        if(controlador.getPolitica() == 3){
            Nodo current = this.readyList.getHead();
            while (current != null) {
                if (((Proceso) current.getValue()).getInstrucciones() - ((Proceso) current.getValue()).getMar() < 
                        process.getInstrucciones()- process.getMar()) {
                    return true;
                }
                current = current.getpNext();
            }    
        }
        return false;
    }

    public void updatePCB(Proceso process, int programCounter, int memoryAddressRegister, String state) {
        process.setEstado(state);
        process.setPc(programCounter);
        process.setMar(memoryAddressRegister);
        process.setTiempoEspera(0);

        switch (state) {
            case "Bloqueado":
                blockedList.appendLast(process);
                logger.logEvent("Proceso " + process.getNombre() + " (ID: " + process.getId() + ") entra en estado de bloqueo");
                break;
            case "Listo":
                readyList.appendLast(process);
                break;
            case "Suspendido-Listo":
                suspendedReadyList.appendLast(process);
                logger.logEvent("Proceso " + process.getNombre() + " (ID: " + process.getId() + ") suspendido (Listo)");
                break;
            case "Suspendido-Bloqueado":
                suspendedBlockedList.appendLast(process);
                logger.logEvent("Proceso " + process.getNombre() + " (ID: " + process.getId() + ") suspendido (Bloqueado)");
                break;
            case "Terminado":
                exitList.appendLast(process);
                logger.logEvent("Proceso " + process.getNombre() + " (ID: " + process.getId() + ") TERMINADO");
                break;
            default:
                exitList.appendLast(process);
                break;
        }

        updateAllLists();
    }

    public void updatePCB(Proceso process, String state) {
        process.setEstado(state);
        process.setTiempoEspera(0);

        switch (state) {
            case "Bloqueado":
                blockedList.appendLast(process);
                logger.logEvent("Proceso " + process.getNombre() + " (ID: " + process.getId() + ") entra en estado de bloqueo");
                break;
            case "Listo":
                readyList.appendLast(process);
                break;
            case "Suspendido-Listo":
                suspendedReadyList.appendLast(process);
                logger.logEvent("Proceso " + process.getNombre() + " (ID: " + process.getId() + ") suspendido (Listo)");
                break;
            case "Suspendido-Bloqueado":
                suspendedBlockedList.appendLast(process);
                logger.logEvent("Proceso " + process.getNombre() + " (ID: " + process.getId() + ") suspendido (Bloqueado)");
                break;
            case "Terminado":
                exitList.appendLast(process);
                logger.logEvent("Proceso " + process.getNombre() + " (ID: " + process.getId() + ") TERMINADO");
                break;
            default:
                exitList.appendLast(process);
                break;
        }

        updateAllLists();
    }

    public void updateAllLists() {
        updateReadyList();
        updateBlockedList();
        updateSuspendedLists();
        updateexitList();
        updateProcessList();
    }
    
    private void updateSuspendedLists() {
        StringBuilder displayReady = new StringBuilder();
        StringBuilder displayBlocked = new StringBuilder();

        Nodo pAux = suspendedReadyList.getHead();
        while (pAux != null) {
            Proceso p = (Proceso) pAux.getValue();
            displayReady.append("\n ----------------------------------\n ")
                .append("ID: ").append(p.getId())
                .append("\n Nombre: ").append(p.getNombre());
            pAux = pAux.getpNext();
        }

        pAux = suspendedBlockedList.getHead();
        while (pAux != null) {
            Proceso p = (Proceso) pAux.getValue();
            displayBlocked.append("\n ----------------------------------\n ")
                .append("ID: ").append(p.getId())
                .append("\n Nombre: ").append(p.getNombre());
            pAux = pAux.getpNext();
        }

        controlador.setListosSuspendidosText(displayReady.toString());
        controlador.setBloqueadosSuspendidosText(displayBlocked.toString());
    }

    public void updateWaitingTime(){
        if(selectedAlgorithm != controlador.getPolitica()){
            selectedAlgorithm = controlador.getPolitica();
            sortReadyQueue(selectedAlgorithm);
            this.updateReadyList();
        }
        Nodo pAux = this.readyList.getHead();
        while(pAux!=null){
            Proceso process = (Proceso)pAux.getValue();
            int time = process.getTiempoEspera();
            process.setTiempoEspera(time+1);
            pAux = pAux.getpNext();
        }
        this.updateProcessList();
    }
    
    public void updateBlockToReady(int id){
        Nodo pAux = this.blockedList.getHead();
        while(pAux!=null){
            if(id== ((Proceso)pAux.getValue()).getId()){
                ((Proceso)pAux.getValue()).setEstado("Listo");
                ((Proceso)pAux.getValue()).setTiempoEspera(0);
                blockedList.delete(pAux);
                readyList.appendLast(pAux);
                logger.logEvent("Proceso (ID: " + id + ") sale de bloqueo y entra a cola de listos");
                break;                
            }
            pAux = pAux.getpNext();
        }
        
        this.updateBlockedList();
        this.updateReadyList();
        this.updateProcessList();
    }
    
    public void updateProcessList(){
        Nodo pAux = allProcessList.getHead();
        String display = "";
        while(pAux!=null){
            Proceso process=(Proceso) pAux.getValue();
            display += this.stringInterfaz(process);
            pAux = pAux.getpNext();
        }
        controlador.setPcbs(display);
    }
    
    public void updateReadyList(){
        Nodo pAux = readyList.getHead();
        String display = "";
        while(pAux!=null){
            Proceso process=(Proceso) pAux.getValue();
            
            display += "\n ----------------------------------\n "
                    + "ID: " + process.getId() +
                      "\n Nombre: " + process.getNombre();
            pAux = pAux.getpNext();
        }
        controlador.setListosText(display);
    }

    public void updateBlockedList(){
        Nodo pAux = blockedList.getHead();
        String display = "";
        while(pAux!=null){
            Proceso process=(Proceso) pAux.getValue();
            
            display += "\n ----------------------------------\n "
                    + "ID: " + process.getId() +
                      "\n Nombre: " + process.getNombre();
            pAux = pAux.getpNext();
        }
        controlador.setBloqueadosText(display);
    }
    
    public void updateexitList(){
        Nodo pAux = exitList.getHead();
        String display = "";
        while(pAux!=null){
            Proceso process=(Proceso) pAux.getValue();
            
            display += "\n ----------------------------------\n "
                    + "Id: " + process.getId() +
                      "\n Nombre: " + process.getNombre();
            pAux = pAux.getpNext();
        }
        controlador.setSalidaText(display);
    }
    
    public String stringInterfaz(Proceso currentProcess){
        String display = "\n ----------------------------------\n Id: " + currentProcess.getId() + 
                "\n Estado: " + currentProcess.getEstado()+ 
                "\n Nombre: " + currentProcess.getNombre() +
                "\n PC: " + currentProcess.getPc() + 
                "\n MAR: " + currentProcess.getMar() +
                "\n Espera: " + currentProcess.getTiempoEspera();
        return display;
    }
    
    public EventLogger getLogger() {
        return logger;
    }
}