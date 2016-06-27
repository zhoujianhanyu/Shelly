/**
 *
 * Copyright 2016 Xiaofei
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package xiaofei.library.shelly;

import android.support.v4.util.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import xiaofei.library.shelly.function.Action0;
import xiaofei.library.shelly.function.Action1;
import xiaofei.library.shelly.function.Function1;
import xiaofei.library.shelly.function.Function2;
import xiaofei.library.shelly.function.TargetAction0;
import xiaofei.library.shelly.function.TargetAction1;
import xiaofei.library.shelly.operator.EmptyOperator;
import xiaofei.library.shelly.operator.FilterOperator;
import xiaofei.library.shelly.operator.FlatMapOperator;
import xiaofei.library.shelly.operator.MapOperator;
import xiaofei.library.shelly.operator.MapOperator2;
import xiaofei.library.shelly.operator.ReducerOperator;
import xiaofei.library.shelly.util.DominoCenter;
import xiaofei.library.shelly.util.Player;
import xiaofei.library.shelly.util.TargetCenter;
import xiaofei.library.shelly.util.Task;
import xiaofei.library.shelly.util.Triple;
import xiaofei.library.shelly.scheduler.BackgroundQueueScheduler;
import xiaofei.library.shelly.scheduler.BackgroundScheduler;
import xiaofei.library.shelly.scheduler.DefaultScheduler;
import xiaofei.library.shelly.scheduler.NewThreadScheduler;
import xiaofei.library.shelly.scheduler.Scheduler;
import xiaofei.library.shelly.scheduler.ThrottleScheduler;
import xiaofei.library.shelly.scheduler.UiThreadScheduler;

/**
 * Created by Xiaofei on 16/5/26.
 */
public class Domino<T, R> {

    protected static final DominoCenter DOMINO_CENTER = DominoCenter.getInstance();

    protected static final TargetCenter TARGET_CENTER = TargetCenter.getInstance();

    private Player<T, R> mPlayer;

    private Object mLabel;

    Domino(Object label) {
        this(label, new Player<T, R>() {
            @Override
            public Scheduler<R> call(List<T> input) {
                return (Scheduler<R>) new DefaultScheduler<T>(input);
            }
        });
    }

    protected Domino(Object label, Player<T, R> player) {
        mLabel = label;
        mPlayer = player;
    }

    public Object getLabel() {
        return mLabel;
    }

    protected Player<T, R> getPlayer() {
        return mPlayer;
    }

    /**
     * T是原本输入的参数类型，R是将传给下面的参数类型！
     *
     */

    public <U> Domino<T, R> target(final Class<? extends U> clazz, final TargetAction0<? super U> targetAction0) {
        return new Domino<T, R>(mLabel, new Player<T, R>() {
            @Override
            public Scheduler<R> call(List<T> input) {
                final Scheduler<R> scheduler = mPlayer.call(input);
                scheduler.play(new Player<R, R>() {
                    @Override
                    public Scheduler<R> call(List<R> input) {
                        CopyOnWriteArrayList<Object> objects = TARGET_CENTER.getObjects(clazz);
                        for (Object object : objects) {
                            targetAction0.call(clazz.cast(object));
                        }
                        return scheduler;
                    }
                });
                return scheduler;
            }
        });
    }

    public <U> Domino<T, R> target(final Class<? extends U> clazz, final TargetAction1<? super U, ? super R> targetAction1) {
        return new Domino<T, R>(mLabel, new Player<T, R>() {
            @Override
            public Scheduler<R> call(List<T> input) {
                final Scheduler<R> scheduler = mPlayer.call(input);
                scheduler.play(new Player<R, R>() {
                    @Override
                    public Scheduler<R> call(List<R> input) {
                        CopyOnWriteArrayList<Object> objects = TARGET_CENTER.getObjects(clazz);
                        for (Object object : objects) {
                            for (R singleInput : input) {
                                targetAction1.call(clazz.cast(object), singleInput);
                            }
                        }
                        return scheduler;
                    }
                });
                return scheduler;
            }
        });
    }

    public Domino<T, R> target(final Action0 action0) {
        return new Domino<T, R>(mLabel, new Player<T, R>() {
            @Override
            public Scheduler<R> call(List<T> input) {
                final Scheduler<R> scheduler = mPlayer.call(input);
                scheduler.play(new Player<R, R>() {
                    @Override
                    public Scheduler<R> call(List<R> input) {
                        action0.call();
                        return scheduler;
                    }
                });
                return scheduler;
            }
        });
    }

    public Domino<T, R> target(final Action1<? super R> action1) {
        return new Domino<T, R>(mLabel, new Player<T, R>() {
            @Override
            public Scheduler<R> call(List<T> input) {
                final Scheduler<R> scheduler = mPlayer.call(input);
                scheduler.play(new Player<R, R>() {
                    @Override
                    public Scheduler<R> call(List<R> input) {
                        for (R singleInput : input) {
                            action1.call(singleInput);
                        }
                        return scheduler;
                    }
                });
                return scheduler;
            }
        });
    }

    public Domino<T, R> target(final Domino<? super R, ?> domino) {
        return new Domino<T, R>(mLabel, new Player<T, R>() {
            @Override
            public Scheduler<R> call(List<T> input) {
                final Scheduler<R> scheduler = mPlayer.call(input);
                scheduler.play(new Player<R, R>() {
                    @Override
                    public Scheduler<R> call(List<R> input) {
                        ((Domino<R, ?>) domino).mPlayer.call(input);
                        return scheduler;
                    }
                });
                return scheduler;
            }
        });
    }

    public <U> Domino<T, U> dominoMap(final Domino<? super R, ? extends U> domino) {
        return merge((Domino<R, U>[]) new Domino[]{domino});
    }

    public <U> Domino<T, U> merge(Domino<? super R, ? extends U> domino1, Domino<? super R, ? extends U> domino2) {
        return merge((Domino<R, U>[]) new Domino[]{domino1, domino2});
    }

    public <U> Domino<T, U> merge(Domino<? super R, ? extends U> domino1, Domino<? super R, ? extends U> domino2, Domino<? super R, ? extends U> domino3) {
        return merge((Domino<R, U>[]) new Domino[]{domino1, domino2, domino3});
    }

    public <U> Domino<T, U> merge(final Domino<? super R, ? extends U>[] dominoes) {
        return new Domino<T, U>(mLabel, new Player<T, U>() {
            @Override
            public Scheduler<U> call(List<T> input) {
                final Scheduler<R> scheduler = mPlayer.call(input);
                List<Function1<CopyOnWriteArrayList<R>, CopyOnWriteArrayList<U>>> functions =
                        new ArrayList<Function1<CopyOnWriteArrayList<R>, CopyOnWriteArrayList<U>>>();
                for (final Domino<? super R, ? extends U> domino : dominoes) {
                    functions.add(new Function1<CopyOnWriteArrayList<R>, CopyOnWriteArrayList<U>>() {
                        @Override
                        public CopyOnWriteArrayList<U> call(CopyOnWriteArrayList<R> input) {
                            Scheduler<U> scheduler = ((Domino<R, U>) domino).mPlayer.call(input);
                            return (CopyOnWriteArrayList<U>) scheduler.waitForFinishing();
                        }
                    });
                }
                return scheduler.scheduleFunction(functions);
            }
        });
    }

    public <S1, S2, V> Domino<T, V> combine(Domino<? super R, S1> domino1, Domino<? super R, S2> domino2,
                                            final Function2<? super S1, ? super S2, ? extends V> combiner) {
        /**
         * 想实现的效果是domino1和domino2分开运行，结果经过combiner结合，得到一堆新的结果
         * 为了实现方便，做如下变化：
         * 1、将domino1与domino2分开运行，返回结果变为一个list，这个单独作为一个结果：domino->reduce->merge
         * 2、将这个结果进行合并：reduce
         * 3、将这个结果展开：flatMap
         */
        return merge(
                domino1.reduce(new Function1<List<S1>, Pair<Integer, List<Object>>>() {
                    @Override
                    public Pair<Integer, List<Object>> call(List<S1> input) {
                        return new Pair<Integer, List<Object>>(1, (List<Object>) input);
                    }
                }),
                domino2.reduce(new Function1<List<S2>, Pair<Integer, List<Object>>>() {
                    @Override
                    public Pair<Integer, List<Object>> call(List<S2> input) {
                        return new Pair<Integer, List<Object>>(2, (List<Object>) input);
                    }
                }))
                .reduce(new Function1<List<Pair<Integer, List<Object>>>, List<V>>() {
                    @Override
                    public List<V> call(List<Pair<Integer, List<Object>>> input) {
                        if (input.size() != 2) {
                            throw new IllegalStateException("Unknown error! Please report this to Xiaofei.");
                        }
                        List<V> result = new ArrayList<V>();
                        List<Object> input1, input2;
                        if (input.get(0).first == 1) {
                            input1 = input.get(0).second;
                            input2 = input.get(1).second;
                        } else {
                            input1 = input.get(1).second;
                            input2 = input.get(0).second;
                        }
                        for (Object o1 : input1) {
                            for (Object o2 : input2) {
                                result.add(combiner.call((S1) o1, (S2) o2));
                            }
                        }
                        return result;
                    }
                })
                .flatMap(new Function1<List<V>, List<V>>() {
                    @Override
                    public List<V> call(List<V> input) {
                        return input;
                    }
                });
    }

    public <S1, S2, U1, U2, V> Domino<T, V> combineTask(
            TaskDomino<? super R, S1, U1> taskDomino1,
            TaskDomino<? super R, S2, U2> taskDomino2,
            final Function2<? super S1, ? super S2, ? extends V> combiner) {
        return combine(
                taskDomino1.map(new Function1<Triple<Boolean,S1,U1>, S1>() {
                    @Override
                    public S1 call(Triple<Boolean, S1, U1> input) {
                        return input.first ? input.second : null;
                    }
                }),
                taskDomino2.map(new Function1<Triple<Boolean,S2,U2>, S2>() {
                    @Override
                    public S2 call(Triple<Boolean, S2, U2> input) {
                        return input.first ? input.second : null;
                    }
                }),
                combiner);
    }

    public Domino<T, R> background() {
        return new Domino<T, R>(mLabel, new Player<T, R>() {
            @Override
            public Scheduler<R> call(List<T> input) {
                Scheduler<R> scheduler = mPlayer.call(input);
                return new BackgroundScheduler<R>(scheduler);
            }
        });
    }

    /**
     * For unit test only.
     */
    Domino<T, R> newThread() {
        return new Domino<T, R>(mLabel, new Player<T, R>() {
            @Override
            public Scheduler<R> call(List<T> input) {
                Scheduler<R> scheduler = mPlayer.call(input);
                return new NewThreadScheduler<R>(scheduler);
            }
        });
    }

    /**
     * For unit test only.
     */
    Domino<T, R> defaultScheduler() {
        return new Domino<T, R>(mLabel, new Player<T, R>() {
            @Override
            public Scheduler<R> call(List<T> input) {
                Scheduler<R> scheduler = mPlayer.call(input);
                return new DefaultScheduler<R>(scheduler);
            }
        });
    }

    public Domino<T, R> uiThread() {
        return new Domino<T, R>(mLabel, new Player<T, R>() {
            @Override
            public Scheduler<R> call(List<T> input) {
                Scheduler<R> scheduler = mPlayer.call(input);
                return new UiThreadScheduler<R>(scheduler);
            }
        });
    }

    public Domino<T, R> backgroundQueue() {
        return new Domino<T, R>(mLabel, new Player<T, R>() {
            @Override
            public Scheduler<R> call(List<T> input) {
                Scheduler<R> scheduler = mPlayer.call(input);
                return new BackgroundQueueScheduler<R>(scheduler);
            }
        });
    }

    public Domino<T, R> throttle(final long windowDuration, final TimeUnit unit) {
        return new Domino<T, R>(mLabel, new Player<T, R>() {
            @Override
            public Scheduler<R> call(List<T> input) {
                Scheduler<R> scheduler = mPlayer.call(input);
                return new ThrottleScheduler<R>(scheduler, mLabel, windowDuration, unit);
            }
        });
    }

    public <U> Domino<T, U> lift(final Function1<CopyOnWriteArrayList<R>, CopyOnWriteArrayList<U>> function) {
        return new Domino<T, U>(mLabel, new Player<T, U>() {
            @Override
            public Scheduler<U> call(final List<T> input) {
                final Scheduler<R> scheduler = mPlayer.call(input);
                return scheduler.scheduleFunction(Collections.singletonList(function));
            }
        });
    }

    public <U> Domino<T, U> map(Function1<? super R, ? extends U> map) {
        return lift(new MapOperator<R, U>(map));
    }

    public <U, S> Domino<T, U> map(Class<S> clazz, Function2<? super S, ? super R, ? extends U> map) {
        return lift(new MapOperator2<R, U, S>(clazz, map));
    }

    public <U> Domino<T, U> flatMap(Function1<? super R, List<U>> map) {
        return lift(new FlatMapOperator<R, U>(map));
    }

    public Domino<T, R> filter(Function1<? super R, Boolean> filter) {
        return lift(new FilterOperator<R>(filter));
    }

    //scheduler的函数是一个高阶函数
    public <U> Domino<T, U> reduce(final Function1<List<R>, ? extends U> reducer) {
        return lift(new ReducerOperator<R, U>(reducer));
    }

    public <U> Domino<T, U> clear() {
        return lift(new EmptyOperator<R, U>());
    }

    public <U, S> TaskDomino<T, U, S> beginTask(Task<R, U, S> task) {
        Domino<T, Triple<Boolean, U, S>> domino = map(new TaskFunction<R, U, S>(task));
        return new TaskDomino<T, U, S>(domino.getLabel(), domino.getPlayer());
    }

    public void play(CopyOnWriteArrayList<T> input) {
        mPlayer.call(input);
    }

    public void commit() {
        DOMINO_CENTER.commit(this);
    }

    private static class TaskFunction<T, R, U> implements Function1<T, Triple<Boolean, R, U>>, Task.TaskListener<R, U> {

        private Task<T, R, U> mTask;

        private volatile R mResult;

        private volatile U mError;

        private volatile int mFlag;

        private Lock mLock = new ReentrantLock();

        private Condition mCondition = mLock.newCondition();

        TaskFunction(Task<T, R, U> task) {
            task.setListener(this);
            mTask = task;
        }

        @Override
        public void onFailure(U error) {
            mLock.lock();
            mError = error;
            mFlag = 0;
            mCondition.signalAll();
            mLock.unlock();
        }

        @Override
        public void onSuccess(R result) {
            mLock.lock();
            mResult = result;
            mFlag = 1;
            mCondition.signalAll();
            mLock.unlock();
        }

        @Override
        public Triple<Boolean, R, U> call(T input) {
            mResult = null;
            mError = null;
            mFlag = -1;
            mTask.execute(input);
            try {
                mLock.lock();
                while (mFlag == -1) {
                    mCondition.await();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                mLock.unlock();
            }
            return new Triple<Boolean, R, U>(mFlag == 1, mResult, mError);
        }

    }

    //TODO 上面结构重构

}
