package pvsys.mauro.heartcheck;

public interface Callbacks  {

    interface Callback <T1 extends Object>{
        void call(T1 arg1);
    }

    interface Callback2 <T1 extends Object, T2 extends Object>{
        void call(T1 arg1, T2 arg2);
    }
}
