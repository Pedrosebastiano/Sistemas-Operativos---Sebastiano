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
    private MemoryManager memoryManager;

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
        this.memoryManager = new MemoryManager();
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
    
    public EventLogger getLogger() {
        return logger;
    }

    public MemoryManager getMemoryManager() {
        return memoryManager;
    }
    
    public Proceso getProcess(){
        Proceso output = null;
        
        tryReactivateSuspendedProcesses();
        
        if(this.readyList.isEmpty()){
            if(selectedAlgorithm != controlador.getPolitica()){
                selectedAlgorithm = controlador.getPolitica();
                sortReadyQueue(selectedAlgorithm);
            }
            
            Nodo pAux = this.readyList.getHead();
            this.readyList.delete(pAux);
            output = (Proceso) pAux.getValue();
            output.setEstado("Ejecucion");
            
            if (!memoryManager.allocate(output.getMemoriaRequerida())) {
                logger.logEvent("ADVERTENCIA: No se pudo asignar memoria para Proceso " + 
                               output.getNombre() + " (ID: " + output.getId() + ")");
            }
            
            logger.logEvent("Procesador selecciona Proceso " + output.getNombre() + 
                          " (ID: " + output.getId() + ")");
        }
        
        this.updateReadyList();
        this.updateProcessList();
        
        return output;    
    }

    private void tryReactivateSuspendedProcesses() {
        Nodo current = suspendedReadyList.getHead();
        while (current != null) {
            Proceso p = (Proceso) current.getValue();
            Nodo next = current.getpNext();
            
            if (memoryManager.canAllocate(p.getMemoriaRequerida())) {
                suspendedReadyList.delete(current);
                p.reactivar();
                readyList.appendLast(p);
                sortReadyQueue(selectedAlgorithm);
                logger.logEvent("Proceso " + p.getNombre() + " (ID: " + p.getId() + 
                              ") reactivado de Suspendido-Listo");
            }
            current = next;
        }

        current = suspendedBlockedList.getHead();
        while (current != null) {
            Proceso p = (Proceso) current.getValue();
            Nodo next = current.getpNext();
            
            if (memoryManager.canAllocate(p.getMemoriaRequerida())) {
                suspendedBlockedList.delete(current);
                p.reactivar();
                blockedList.appendLast(p);
                logger.logEvent("Proceso " + p.getNombre() + " (ID: " + p.getId() + 
                              ") reactivado de Suspendido-Bloqueado");
            }
            current = next;
        }
    }
    
    private void sortReadyQueue(int schedulingAlgorithm) {
        switch (schedulingAlgorithm) {
            case 0:
                readyList = sortByWaitingTime(readyList);
                logger.logEvent("Cambio de algoritmo a FCFS");
                break;
            case 1:
                readyList = sortByWaitingTime(readyList);
                logger.logEvent("Cambio de algoritmo a Round Robin");
                break;
            case 2:
                readyList = sortByDuration(readyList);
                logger.logEvent("Cambio de algoritmo a SPN");
                break;
            case 3:
                readyList = sortByRemainingTime(readyList);
                logger.logEvent("Cambio de algoritmo a SRT");
                break;
            case 4:
                readyList = sortByHRR(readyList);
                logger.logEvent("Cambio de algoritmo a HRRN");
                break;
            case 5:
                readyList = sortByPriority(readyList);
                logger.logEvent("Cambio de algoritmo a Prioridad");
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

    private List sortByPriority(List list) {
        return bubbleSort(list, (p1, p2) -> Integer.compare(
            ((Proceso) p1).getPrioridad(),
            ((Proceso) p2).getPrioridad()
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
        if(controlador.getPolitica() == 3 && this.readyList.isEmpty()){
            int currentRemainingTime = process.getInstrucciones() - process.getMar();
            
            Nodo current = this.readyList.getHead();
            while (current != null) {
                Proceso readyProcess = (Proceso) current.getValue();
                int readyRemainingTime = readyProcess.getInstrucciones() - readyProcess.getMar();
                
                if (readyRemainingTime < currentRemainingTime) {
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

        handleStateTransition(process, state);
    }

    public void updatePCB(Proceso process, String state) {
        process.setEstado(state);
        process.setTiempoEspera(0);

        handleStateTransition(process, state);
    }

    private void handleStateTransition(Proceso process, String state) {
        switch (state) {
            case "Bloqueado":
                if (process.debeSuspenderse(memoryManager.getAvailableMemory())) {
                    process.suspender();
                    suspendedBlockedList.appendLast(process);
                    memoryManager.deallocate(process.getMemoriaRequerida());
                    logger.logEvent("Proceso " + process.getNombre() + " (ID: " + process.getId() + 
                                  ") suspendido (Bloqueado) por falta de memoria");
                } else {
                    blockedList.appendLast(process);
                    logger.logEvent("Proceso " + process.getNombre() + " (ID: " + process.getId() + 
                                  ") entra en estado de bloqueo por operación I/O");
                }
                break;
            case "Listo":
                if (process.debeSuspenderse(memoryManager.getAvailableMemory())) {
                    process.suspender();
                    suspendedReadyList.appendLast(process);
                    memoryManager.deallocate(process.getMemoriaRequerida());
                    logger.logEvent("Proceso " + process.getNombre() + " (ID: " + process.getId() + 
                                  ") suspendido (Listo) por falta de memoria");
                } else {
                    readyList.appendLast(process);
                    sortReadyQueue(selectedAlgorithm);
                }
                break;
            case "Suspendido-Listo":
                suspendedReadyList.appendLast(process);
                memoryManager.deallocate(process.getMemoriaRequerida());
                logger.logEvent("Proceso " + process.getNombre() + " (ID: " + process.getId() + 
                              ") suspendido (Listo)");
                break;
            case "Suspendido-Bloqueado":
                suspendedBlockedList.appendLast(process);
                memoryManager.deallocate(process.getMemoriaRequerida());
                logger.logEvent("Proceso " + process.getNombre() + " (ID: " + process.getId() + 
                              ") suspendido (Bloqueado)");
                break;
            case "Terminado":
                exitList.appendLast(process);
                memoryManager.deallocate(process.getMemoriaRequerida());
                logger.logEvent("Proceso " + process.getNombre() + " (ID: " + process.getId() + ") TERMINADO");
                break;
            default:
                exitList.appendLast(process);
                memoryManager.deallocate(process.getMemoriaRequerida());
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
                .append("\n Nombre: ").append(p.getNombre())
                .append("\n Memoria: ").append(p.getMemoriaRequerida());
            pAux = pAux.getpNext();
        }

        pAux = suspendedBlockedList.getHead();
        while (pAux != null) {
            Proceso p = (Proceso) pAux.getValue();
            displayBlocked.append("\n ----------------------------------\n ")
                .append("ID: ").append(p.getId())
                .append("\n Nombre: ").append(p.getNombre())
                .append("\n Memoria: ").append(p.getMemoriaRequerida());
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
                Proceso p = (Proceso)pAux.getValue();
                p.setEstado("Listo");
                p.setTiempoEspera(0);
                blockedList.delete(pAux);
                
                if (p.debeSuspenderse(memoryManager.getAvailableMemory())) {
                    p.suspender();
                    suspendedReadyList.appendLast(pAux);
                    memoryManager.deallocate(p.getMemoriaRequerida());
                    logger.logEvent("Proceso (ID: " + id + ") sale de bloqueo pero es suspendido por falta de memoria");
                } else {
                    readyList.appendLast(pAux);
                    sortReadyQueue(selectedAlgorithm);
                    logger.logEvent("Proceso (ID: " + id + ") sale de bloqueo (operación I/O completada) y entra a cola de listos");
                }
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
                "\n Espera: " + currentProcess.getTiempoEspera() +
                "\n Prioridad: " + currentProcess.getPrioridad() +
                "\n Memoria: " + currentProcess.getMemoriaRequerida();
        return display;
    }
}