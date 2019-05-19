/*
 *  Klasa agenta kupującego książki
 *
 *  Argumenty projektu (NETBEANS: project properties/run/arguments):
 *  -agents seller1:BookSellerAgent();seller2:BookSellerAgent();buyer1:BookBuyerAgent(Zamek) -gui
 *
 * Wersja c:
   kupiec początkowo odpowiada sprzedawcy ceną o 40% niższą, a następnie zawsze podwyższa
   cenę o stałą wartość, np. 6, maksymalnie 8 razy,
   sprzedawca zawsze wybiera cenę średnią spośród dwóch ostatnich propozycji (swojej i kupca),
   kupiec zamawia książkę, gdy ostatnia cena sprzedawcy różni się co najwyżej o 3 od ceny
   ostatnio przez siebie zaproponowanej.
 *
 *
 *
 */

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.*;
import jade.lang.acl.*;

import java.util.*;

public class BookBuyerAgent extends Agent {

    private String targetBookTitle;    // tytuł kupowanej książki przekazywany poprzez argument wejściowy
    // lista znanych agentów sprzedających książki (w przypadku użycia żółtej księgi - usługi katalogowej, sprzedawcy
    // mogą być dołączani do listy dynamicznie!
    private AID[] sellerAgents = {
            new AID("seller1", AID.ISLOCALNAME),
            new AID("seller2", AID.ISLOCALNAME)};

    // Inicjalizacja klasy agenta:
    protected void setup() {

        //doWait(5000);   // Oczekiwanie na uruchomienie agentów sprzedających

        System.out.println("Witam! Agent-kupiec " + getAID().getName() + " (wersja b 2018/19) jest gotów!");

        Object[] args = getArguments();  // lista argumentów wejściowych (tytuł książki)

        if (args != null && args.length > 0)   // jeśli podano tytuł książki
        {
            targetBookTitle = (String) args[0];
            System.out.println("Zamierzam kupić książkę zatytułowaną " + targetBookTitle);

            addBehaviour(new RequestPerformer());  // dodanie głównej klasy zachowań - kod znajduje się poniżej

        } else {
            // Jeśli nie przekazano poprzez argument tytułu książki, agent kończy działanie:
            System.out.println("Należy podać tytuł książki w argumentach wejściowych kupca!");
            doDelete();
        }
    }

    // Metoda realizująca zakończenie pracy agenta:
    protected void takeDown() {
        System.out.println("Agent-kupiec " + getAID().getName() + " kończy istnienie.");
    }

    /**
     * Inner class RequestPerformer.
     * This is the behaviour used by Book-buyer agents to request seller
     * agents the target book.
     */
    private class RequestPerformer extends Behaviour {

        private AID bestSeller;     // agent sprzedający z najkorzystniejszą ofertą
        private int initialPrice;
        private int bestPrice;      // najlepsza cena
        private int myDealPrice;
        private int sellerPriceReply;
        private double fortyPercentLowerDeal = 0.6;
        private int repliesCnt = 0; // liczba odpowiedzi od agentów
        private MessageTemplate mt; // szablon odpowiedzi
        private int step = 0;       // krok
        private int negotiationCounter = 0;

        public void action() {
            switch (step) {
                case 0:      // wysłanie oferty kupna
                    System.out.print(" Oferta kupna (CFP) jest wysyłana do: ");
                    for (int i = 0; i < sellerAgents.length; ++i) {
                        System.out.print(sellerAgents[i] + " ");
                    }
                    System.out.println();

                    // Tworzenie wiadomości CFP do wszystkich sprzedawców:
                    ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
                    for (int i = 0; i < sellerAgents.length; ++i) {
                        cfp.addReceiver(sellerAgents[i]);          // dodanie adresate
                    }
                    cfp.setContent(targetBookTitle);             // wpisanie zawartości - tytułu książki
                    cfp.setConversationId("book-trade");         // wpisanie specjalnego identyfikatora korespondencji
                    cfp.setReplyWith("cfp" + System.currentTimeMillis()); // dodatkowa unikatowa wartość, żeby w razie odpowiedzi zidentyfikować adresatów
                    myAgent.send(cfp);                           // wysłanie wiadomości

                    // Utworzenie szablonu do odbioru ofert sprzedaży tylko od wskazanych sprzedawców:
                    mt = MessageTemplate.and(MessageTemplate.MatchConversationId("book-trade"),
                            MessageTemplate.MatchInReplyTo(cfp.getReplyWith()));
                    step = 1;     // przejście do kolejnego kroku
                    break;
                case 1:      // odbiór ofert sprzedaży/odmowy od agentów-sprzedawców
                    ACLMessage reply = myAgent.receive(mt);      // odbiór odpowiedzi
                    if (reply != null) {
                        if (reply.getPerformative() == ACLMessage.PROPOSE)   // jeśli wiadomość jest typu PROPOSE
                        {
                            int price = Integer.parseInt(reply.getContent());  // cena książki
                            if (bestSeller == null || price < bestPrice)       // jeśli jest to najlepsza oferta
                            {
                                bestPrice = price;
                                bestSeller = reply.getSender();
                            }
                        }

                        repliesCnt++;                                        // liczba ofert
                        if (repliesCnt >= sellerAgents.length)               // jeśli liczba ofert co najmniej liczbie sprzedawców
                        {
                            step = 5; //Mechanizm negocjacji
                        }
                    } else {
                        block();
                    }
                    break;
                case 5: /* NEGOCJACJA Z NAJLEPSZYM SPRZEDAWCĄ o 40% NIŻSZEJ CENY */ {
                    initialPrice = bestPrice;
                    myDealPrice = (int) (bestPrice * fortyPercentLowerDeal); //Dla najlepszej oferty wyślij odpowiedź z ceną o 40% niższą niż pierwotna.

                    System.out.println("Kupujący rozpoczyna negocjacje ze sprzedawcą: " + bestSeller.getName().substring(0,7));
                    System.out.println("Agent-kupujący proponuje o 40% mniejszą kwotę: " + myDealPrice);

                    ACLMessage newDeal = new ACLMessage(ACLMessage.PROPOSE);
                    newDeal.addReceiver(bestSeller);          // dodanie adresate
                    newDeal.setContent(String.valueOf(myDealPrice));             // wpisanie zawartości - tytułu książki
                    newDeal.setConversationId("book-trade");         // wpisanie specjalnego identyfikatora korespondencji
                    newDeal.setReplyWith("deal" + System.currentTimeMillis()); // dodatkowa unikatowa wartość, żeby w razie odpowiedzi zidentyfikować adresatów
                    myAgent.send(newDeal);                           // wysłanie wiadomości

                    mt = MessageTemplate.and(MessageTemplate.MatchConversationId("book-trade"),
                            MessageTemplate.MatchInReplyTo(newDeal.getReplyWith()));

                    step = 6;
                    break;
                }
                case 6: /* ODBIÓR CENY OD SPRZEDAWCY I PROPOZYCJA WYŻSZEJ CENY */
                    ACLMessage dealReply = myAgent.receive();      // odbiór odpowiedzi
                    if (dealReply != null) {
                        if (dealReply.getPerformative() == ACLMessage.PROPOSE)   // jeśli wiadomość jest typu PROPOSE
                        {
                            sellerPriceReply = Integer.parseInt(dealReply.getContent());  // cena książki

                            //JEśLI priceReply różni się od mojej ceny o co najwyżej 3 to kup książkę!
                            //w przeciwnym razie dalej negocjuj
                            if (sellerPriceReply <= myDealPrice + 3 &&
                                    sellerPriceReply <= initialPrice) {
                                step = 2;
                            } else {
                                step = 7; // NEGOCJUJ DALEJ
                            }
                        }
                    } else {
                        ACLMessage newDeal = new ACLMessage(ACLMessage.PROPOSE);
                        newDeal.addReceiver(bestSeller);          // dodanie adresate
                        newDeal.setContent(String.valueOf(myDealPrice));             // wpisanie zawartości - tytułu książki
                        newDeal.setConversationId("book-trade");         // wpisanie specjalnego identyfikatora korespondencji
                        newDeal.setReplyWith("deal" + System.currentTimeMillis()); // dodatkowa unikatowa wartość, żeby w razie odpowiedzi zidentyfikować adresatów
                        myAgent.send(newDeal);
                    }
                    break;
                case 7: {//DALSZA NEGOCJACJA I PODWYŻSZENIE CENY O STAŁĄ WARTOSC
                    if(++negotiationCounter >= 8) { // JESLI ODBYTO JUZ PONAD 5 NEGOCJACJI TO PIERDOL SPRZEDAWCE!
                        System.out.println("Negocjacja trwała zbyt długo, nie doszło do transakcji.");
                        myAgent.doDelete();
                        step = 4;
                    }

                    myDealPrice = (int)(myDealPrice * 1.1);
                    System.out.println("Agent-kupujący proponuje cenę: " + myDealPrice);

                    ACLMessage newDeal = new ACLMessage(ACLMessage.PROPOSE);
                    newDeal.addReceiver(bestSeller);          // dodanie adresate
                    newDeal.setContent(String.valueOf(myDealPrice));             // wpisanie zawartości - tytułu książki
                    newDeal.setConversationId("book-trade");         // wpisanie specjalnego identyfikatora korespondencji
                    newDeal.setReplyWith("deal" + System.currentTimeMillis()); // dodatkowa unikatowa wartość, żeby w razie odpowiedzi zidentyfikować adresatów
                    myAgent.send(newDeal);                           // wysłanie wiadomości

                    step = 6;
                    break;
                }
                case 2:      // wysłanie zamówienia do sprzedawcy, który złożył najlepszą ofertę
                    ACLMessage order = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
                    order.addReceiver(bestSeller);
                    order.setContent(targetBookTitle);
                    order.setConversationId("book-trade");
                    order.setReplyWith("order" + System.currentTimeMillis());
                    myAgent.send(order);
                    mt = MessageTemplate.and(MessageTemplate.MatchConversationId("book-trade"),
                            MessageTemplate.MatchInReplyTo(order.getReplyWith()));
                    step = 3;
                    break;
                case 3:      // odbiór odpowiedzi na zamównienie
                    reply = myAgent.receive(mt);
                    if (reply != null) {
                        if (reply.getPerformative() == ACLMessage.INFORM) {
                            System.out.println("Tytuł " + targetBookTitle + " został zamówiony.");
                            System.out.println("Cena = " + myDealPrice);
                            myAgent.doDelete();
                        }
                        step = 4;
                    } else {
                        block();
                    }
                    break;
            }  // switch
        } // action

        public boolean done() {
            return ((step == 2 && bestSeller == null) || step == 4);
        }
    } // Koniec wewnętrznej klasy RequestPerformer
}
