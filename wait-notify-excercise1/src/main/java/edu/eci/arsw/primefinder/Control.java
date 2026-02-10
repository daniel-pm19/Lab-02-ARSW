/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.eci.arsw.primefinder;

import java.util.Scanner;

/**
 *
 */
public class Control extends Thread {

    private final static int NTHREADS = 3;
    private final static int MAXVALUE = 30000000;
    private final static int TMILISECONDS = 5000;
    private final Object lock = new Object();
    private final int NDATA = MAXVALUE / NTHREADS;

    private PrimeFinderThread pft[];
    
    private Control() {
        super();
        this.pft = new  PrimeFinderThread[NTHREADS];

        int i;
        for(i = 0;i < NTHREADS - 1; i++) {
            PrimeFinderThread elem = new PrimeFinderThread(i*NDATA, (i+1)*NDATA, lock);
            pft[i] = elem;
        }
        pft[i] = new PrimeFinderThread(i*NDATA, MAXVALUE + 1, lock);
    }
    
    public static Control newControl() {
        return new Control();
    }

    @Override
    public void run() {
        for(int i = 0;i < NTHREADS;i++ ) {
            pft[i].start();
        }
        
        Scanner sc = new Scanner(System.in);

        boolean isRunning = true;
        while(isRunning){
            try{
                Thread.sleep(TMILISECONDS);

                for(PrimeFinderThread p : pft){
                    p.setStatus(true);
                }

                printResults();

                boolean anyAlive = false;
                for(PrimeFinderThread p : pft){
                    if(p.isAlive()){
                        anyAlive = true;
                    }
                }
                
                if(!anyAlive){
                    isRunning = false;
                } else {
                    System.out.println("press ENTER to continue: "); 
                    sc.nextLine();

                    for(PrimeFinderThread p : pft){
                        p.setStatus(false);
                    }

                    synchronized(lock){
                        lock.notifyAll();
                    }
                }
                
            } catch (InterruptedException e){
                Thread.currentThread().interrupt();
            }
        }   
    }

    public void printResults(){
        int total = 0;
        for(PrimeFinderThread p : pft){
            total += p.getPrimes().size();
        }

        System.out.println("The system have found " + total + " prime numbers");

    }   
}
